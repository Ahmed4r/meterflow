package com.meterflow;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meterflow.billing.BillingService;
import com.meterflow.identity.AuthService;
import com.meterflow.metering.UsageService;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PlatformIntegrationTest {
  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

  @Container
  static GenericContainer<?> redis =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", postgres::getJdbcUrl);
    r.add("spring.datasource.username", postgres::getUsername);
    r.add("spring.datasource.password", postgres::getPassword);
    r.add("spring.data.redis.host", redis::getHost);
    r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    r.add("meterflow.jwt-secret", () -> "test-only-secret-at-least-32-bytes-long-12345");
    r.add("meterflow.secure-cookie", () -> false);
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcClient db;
  @Autowired AuthService auth;
  @Autowired UsageService usage;
  @Autowired BillingService billing;

  record Fixture(UUID org, UUID sub, String key, String jwt, UUID user) {}

  Fixture fixture() {
    var tokens =
        auth.register(
            UUID.randomUUID() + "@example.test", "Synthetic-password-123", "Test User", "Test Org");
    var user =
        db.sql("select user_id from auth_session where refresh_hash=?")
            .param(AuthService.hash(tokens.refreshToken()))
            .query(UUID.class)
            .single();
    UUID
        org =
            db.sql("select organization_id from membership where user_id=?")
                .param(user)
                .query(UUID.class)
                .single(),
        customer = UUID.randomUUID(),
        plan = UUID.randomUUID(),
        sub = UUID.randomUUID();
    db.sql(
            "insert into customer(id,organization_id,name,reference) values (?,?,'Synthetic Customer','test')")
        .params(customer, org)
        .update();
    db.sql(
            "insert into plan(id,organization_id,name,currency,base_price) values (?,?,'Test','EUR',29)")
        .params(plan, org)
        .update();
    db.sql("insert into pricing_tier values (?,?,0,null,0.005)")
        .params(UUID.randomUUID(), plan)
        .update();
    db.sql(
            "insert into subscription(id,organization_id,customer_id,plan_id,status,starts_at) values (?,?,?,?,'ACTIVE',?)")
        .params(sub, org, customer, plan, OffsetDateTime.now().minusMonths(2))
        .update();
    String key = "mf_" + AuthService.randomToken();
    db.sql(
            "insert into api_key(id,organization_id,name,token_hash,prefix) values (?,?,'test',?,'mf_test')")
        .params(UUID.randomUUID(), org, AuthService.hash(key))
        .update();
    return new Fixture(org, sub, key, tokens.accessToken(), user);
  }

  @Test
  void concurrentRetryPersistsOnceAndConflictingPayloadRejected() throws Exception {
    var f = fixture();
    var event =
        new UsageService.Event(
            f.sub,
            "search",
            125,
            Instant.now().minusSeconds(10).truncatedTo(java.time.temporal.ChronoUnit.MICROS));
    try (var executor = Executors.newFixedThreadPool(8)) {
      List<Callable<UsageService.Receipt>> tasks = new ArrayList<>();
      for (int i = 0; i < 16; i++) tasks.add(() -> usage.ingest(f.key, "same-event", event));
      var results = executor.invokeAll(tasks);
      Set<UUID> ids = new HashSet<>();
      for (var result : results) ids.add(result.get().id());
      assertThat(ids).hasSize(1);
    }
    assertThat(
            db.sql("select sum(quantity) from usage_event where organization_id=?")
                .param(f.org)
                .query(Long.class)
                .single())
        .isEqualTo(125);
    assertThatThrownBy(
            () ->
                usage.ingest(
                    f.key,
                    "same-event",
                    new UsageService.Event(f.sub, "search", 126, event.occurredAt())))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  }

  @Test
  void billingRetriesReturnSameInvoiceAndClosedMonthRejectsLateUsage() throws Exception {
    var f = fixture();
    LocalDate month = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).minusMonths(1);
    Instant when = month.plusDays(4).atStartOfDay(ZoneOffset.UTC).toInstant();
    usage.ingest(f.key, "last-month", new UsageService.Event(f.sub, "search", 101, when));
    UUID invoice = billing.generate(f.org, f.sub, month);
    assertThat(billing.generate(f.org, f.sub, month)).isEqualTo(invoice);
    assertThat(
            db.sql("select total from invoice where id=?")
                .param(invoice)
                .query(BigDecimal.class)
                .single())
        .isEqualByComparingTo("29.51");
    assertThatThrownBy(
            () -> usage.ingest(f.key, "late", new UsageService.Event(f.sub, "search", 10, when)))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    mvc.perform(
            get("/api/v1/organizations/" + f.org + "/invoices/" + invoice + "/pdf")
                .header("Authorization", "Bearer " + f.jwt))
        .andExpect(status().isOk())
        .andExpect(content().contentType("application/pdf"));
  }

  @Test
  void identicalNanosecondTimestampRetriesAreNotConflicts() {
    var f = fixture();
    Instant precise = Instant.ofEpochSecond(Instant.now().getEpochSecond() - 10, 123456789);
    var event = new UsageService.Event(f.sub, "search", 5, precise);
    var original = usage.ingest(f.key, "nanosecond-retry", event);
    var retry = usage.ingest(f.key, "nanosecond-retry", event);
    assertThat(retry.id()).isEqualTo(original.id());
    assertThat(retry.duplicate()).isTrue();
    assertThat(
            db.sql("select sum(quantity) from usage_event where subscription_id=?")
                .param(f.sub)
                .query(Long.class)
                .single())
        .isEqualTo(5);
  }

  @Test
  void concurrentMonthClosureNeverMissesAcceptedUsage() throws Exception {
    var f = fixture();
    LocalDate month = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).minusMonths(1);
    Instant when = month.plusDays(5).atStartOfDay(ZoneOffset.UTC).toInstant();
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      Future<Boolean> ingestion =
          executor.submit(
              () -> {
                start.await();
                try {
                  usage.ingest(
                      f.key, "closure-race", new UsageService.Event(f.sub, "search", 100, when));
                  return true;
                } catch (org.springframework.web.server.ResponseStatusException e) {
                  assertThat(e.getStatusCode().value()).isEqualTo(409);
                  return false;
                }
              });
      Future<UUID> closure =
          executor.submit(
              () -> {
                start.await();
                return billing.generate(f.org, f.sub, month);
              });
      start.countDown();
      boolean accepted = ingestion.get(15, TimeUnit.SECONDS);
      UUID invoice = closure.get(15, TimeUnit.SECONDS);
      assertThat(
              db.sql("select total from invoice where id=?")
                  .param(invoice)
                  .query(BigDecimal.class)
                  .single())
          .isEqualByComparingTo(accepted ? "29.50" : "29.00");
      assertThat(
              db.sql("select count(*) from usage_event where subscription_id=?")
                  .param(f.sub)
                  .query(Integer.class)
                  .single())
          .isEqualTo(accepted ? 1 : 0);
    }
  }

  @Test
  void crossTenantResourcesAndKeysAreDenied() throws Exception {
    var a = fixture();
    var b = fixture();
    for (String path :
        List.of(
            "/customers",
            "/subscriptions",
            "/invoices",
            "/analytics?month=" + LocalDate.now().withDayOfMonth(1),
            "/api-keys",
            "/members"))
      mvc.perform(
              get("/api/v1/organizations/" + b.org + path)
                  .header("Authorization", "Bearer " + a.jwt))
          .andExpect(status().isForbidden());
    assertThatThrownBy(
            () ->
                usage.ingest(
                    a.key, "foreign", new UsageService.Event(b.sub, "search", 1, Instant.now())))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  }

  @Test
  void refreshRotatesAndLogoutRevokesAccess() throws Exception {
    var tokens =
        auth.register(
            UUID.randomUUID() + "@example.test", "Synthetic-password-123", "Synthetic", "Org");
    var next = auth.refresh(tokens.refreshToken());
    mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + next.accessToken()))
        .andExpect(status().isOk());
    auth.logout(next.refreshToken());
    mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + next.accessToken()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void profileExportAndDeletionRemoveIdentityAndRevokeKeys() throws Exception {
    var f = fixture();
    var response =
        mvc.perform(get("/api/v1/me/export").header("Authorization", "Bearer " + f.jwt))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(response).doesNotContain("password_hash", "refresh_hash", f.key);
    mvc.perform(
            delete("/api/v1/me")
                .header("Authorization", "Bearer " + f.jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"Synthetic-password-123\"}"))
        .andExpect(status().isOk());
    assertThat(
            db.sql("select count(*) from app_user where id=?")
                .param(f.user)
                .query(Integer.class)
                .single())
        .isZero();
    mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + f.jwt))
        .andExpect(status().isUnauthorized());
    assertThatThrownBy(
            () ->
                usage.ingest(
                    f.key, "deleted", new UsageService.Event(f.sub, "search", 1, Instant.now())))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  }

  @Test
  void reusedRefreshTokenCommitsRevocationDespiteUnauthorizedResponse() throws Exception {
    var tokens =
        auth.register(
            UUID.randomUUID() + "@example.test", "Synthetic-password-123", "Synthetic", "Org");
    var rotated = auth.refresh(tokens.refreshToken());
    mvc.perform(
            post("/api/v1/auth/refresh")
                .header("Origin", "http://localhost:3000")
                .cookie(new jakarta.servlet.http.Cookie("mf_refresh", tokens.refreshToken())))
        .andExpect(status().isUnauthorized());
    // This separate request proves the revoke transaction was committed, not rolled back.
    mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + rotated.accessToken()))
        .andExpect(status().isUnauthorized());
    assertThatThrownBy(() -> auth.refresh(rotated.refreshToken()))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  }

  @Test
  void unrelatedInvalidRefreshTokenCannotRevokeValidSession() throws Exception {
    var tokens =
        auth.register(
            UUID.randomUUID() + "@example.test", "Synthetic-password-123", "Synthetic", "Org");
    assertThatThrownBy(() -> auth.refresh(AuthService.randomToken()))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + tokens.accessToken()))
        .andExpect(status().isOk());
  }

  @Test
  void analystIsScopedAndWorksWithoutExternalProcessing() throws Exception {
    var a = fixture();
    var b = fixture();
    String body =
        json.writeValueAsString(
            Map.of(
                "question",
                "Compare billing",
                "month",
                LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1)));
    mvc.perform(
            post("/api/v1/organizations/" + b.org + "/analyst")
                .header("Authorization", "Bearer " + a.jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/organizations/" + a.org + "/analyst")
                .header("Authorization", "Bearer " + a.jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mode").value("DETERMINISTIC"))
        .andExpect(jsonPath("$.facts.currency").value("EUR"));
  }

  @Test
  void subscriptionCancellationIsIdempotentAndStopsIngestion() throws Exception {
    var f = fixture();
    for (int i = 0; i < 2; i++)
      mvc.perform(
              post("/api/v1/organizations/" + f.org + "/subscriptions/" + f.sub + "/cancel")
                  .header("Authorization", "Bearer " + f.jwt))
          .andExpect(status().isOk());
    assertThat(
            db.sql(
                    "select count(*) from subscription_history where subscription_id=? and action='CANCELLED'")
                .param(f.sub)
                .query(Integer.class)
                .single())
        .isEqualTo(1);
    assertThatThrownBy(
            () ->
                usage.ingest(
                    f.key, "cancelled", new UsageService.Event(f.sub, "search", 1, Instant.now())))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  }

  @Test
  void concurrentBillingJobsCreateOneInvoice() throws Exception {
    var f = fixture();
    LocalDate month = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).minusMonths(1);
    try (var executor = Executors.newFixedThreadPool(4)) {
      var tasks = new ArrayList<Callable<UUID>>();
      for (int i = 0; i < 8; i++) tasks.add(() -> billing.generate(f.org, f.sub, month));
      var results = executor.invokeAll(tasks);
      Set<UUID> ids = new HashSet<>();
      for (var result : results) ids.add(result.get());
      assertThat(ids).hasSize(1);
    }
    assertThat(
            db.sql("select count(*) from invoice where subscription_id=?")
                .param(f.sub)
                .query(Integer.class)
                .single())
        .isEqualTo(1);
  }

  @Test
  void refreshRequiresTrustedOriginAndValidationDoesNotExposeInternals() throws Exception {
    mvc.perform(post("/api/v1/auth/refresh").header("Origin", "https://untrusted.example"))
        .andExpect(status().isForbidden());
    var f = fixture();
    mvc.perform(
            get("/api/v1/organizations/" + f.org + "/customers?page=-1")
                .header("Authorization", "Bearer " + f.jwt))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.detail")
                .value("Invalid input. Check required fields, formats and limits."));
    mvc.perform(post("/api/v1/usage-events").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void unauthorizedInvoiceDetailAndPdfReturnNotFoundWithinOwnTenant() throws Exception {
    var a = fixture();
    var b = fixture();
    UUID id =
        billing.generate(
            b.org, b.sub, LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).minusMonths(1));
    for (String suffix : List.of("", "/pdf"))
      mvc.perform(
              get("/api/v1/organizations/" + a.org + "/invoices/" + id + suffix)
                  .header("Authorization", "Bearer " + a.jwt))
          .andExpect(status().isNotFound());
  }

  @Test
  void deletionCannotOrphanOtherMembers() throws Exception {
    var a = fixture();
    var b = fixture();
    db.sql("insert into membership values (?,?,'VIEWER')").params(a.org, b.user).update();
    mvc.perform(
            delete("/api/v1/me")
                .header("Authorization", "Bearer " + a.jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"Synthetic-password-123\"}"))
        .andExpect(status().isConflict());
    assertThat(
            db.sql("select count(*) from app_user where id=?")
                .param(a.user)
                .query(Integer.class)
                .single())
        .isEqualTo(1);
  }

  @Test
  void viewerCannotWriteAndCompositeForeignKeyRejectsTenantMismatch() throws Exception {
    var a = fixture();
    var b = fixture();
    db.sql("insert into membership values (?,?,'VIEWER')").params(b.org, a.user).update();
    mvc.perform(
            post("/api/v1/organizations/" + b.org + "/customers")
                .header("Authorization", "Bearer " + a.jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"No\",\"reference\":\"no\"}"))
        .andExpect(status().isForbidden());
    assertThatThrownBy(
            () ->
                db.sql("update subscription set organization_id=? where id=?")
                    .params(b.org, a.sub)
                    .update())
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }
}
