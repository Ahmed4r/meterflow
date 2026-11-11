package com.meterflow.metering;

import com.meterflow.identity.AuthService;
import com.meterflow.subscriptions.SubscriptionRepository;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UsageService {
  private final JdbcClient db;
  private final SubscriptionRepository subscriptions;
  private final RateLimiter limiter;

  public UsageService(JdbcClient db, SubscriptionRepository subscriptions, RateLimiter limiter) {
    this.db = db;
    this.subscriptions = subscriptions;
    this.limiter = limiter;
  }

  public record Event(
      @NotNull UUID subscriptionId,
      @NotBlank @Pattern(regexp = "[a-zA-Z0-9._-]{1,60}") String service,
      @Min(1) @Max(1000000000) long quantity,
      @NotNull Instant occurredAt) {}

  public record Receipt(UUID id, boolean duplicate) {}

  @Transactional
  public Receipt ingest(String key, String idempotency, Event event) {
    if (key == null || key.length() > 128)
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid API key");
    var k =
        db.sql("select id,organization_id from api_key where token_hash=? and not revoked")
            .param(AuthService.hash(key))
            .query(new org.springframework.jdbc.core.ColumnMapRowMapper())
            .optional()
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid API key"));
    UUID org = (UUID) k.get("organization_id");
    limiter.require("ingestion:" + org, 600);
    if (idempotency == null || !idempotency.matches("[a-zA-Z0-9._:-]{1,128}"))
      throw new IllegalArgumentException("Invalid idempotency key");
    // Lock order is identical in ingestion and invoicing. Closed periods cannot race with writes.
    var sub =
        subscriptions
            .lock(org, event.subscriptionId())
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found"));
    var existing =
        db.sql(
                "select id,subscription_id,service,quantity,occurred_at from usage_event where organization_id=? and idempotency_key=?")
            .params(org, idempotency)
            .query(new org.springframework.jdbc.core.ColumnMapRowMapper())
            .optional();
    if (existing.isPresent()) return replay(existing.get(), event);
    Instant now = Instant.now();
    if (!sub.status().equals("ACTIVE")
        || event.occurredAt().isBefore(sub.startsAt())
        || event.occurredAt().isAfter(now.plusSeconds(60))
        || event.occurredAt().isBefore(now.minus(Duration.ofDays(62))))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Event outside the active ingestion window");
    LocalDate month = event.occurredAt().atZone(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1);
    if (db.sql("select exists(select 1 from invoice where subscription_id=? and period_start=?)")
        .params(sub.id(), month)
        .query(Boolean.class)
        .single())
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Billing period is closed");
    UUID id = UUID.randomUUID();
    int inserted =
        db.sql(
                "insert into usage_event(id,organization_id,subscription_id,idempotency_key,service,quantity,occurred_at) values (?,?,?,?,?,?,?) on conflict (organization_id,idempotency_key) do nothing")
            .params(
                id,
                org,
                sub.id(),
                idempotency,
                event.service(),
                event.quantity(),
                // Normalize before JDBC: PostgreSQL rounds nanoseconds to microseconds,
                // but replay comparison uses truncation. Both paths must use one rule.
                event
                    .occurredAt()
                    .truncatedTo(java.time.temporal.ChronoUnit.MICROS)
                    .atOffset(ZoneOffset.UTC))
            .update();
    if (inserted == 0)
      return replay(
          db.sql(
                  "select id,subscription_id,service,quantity,occurred_at from usage_event where organization_id=? and idempotency_key=?")
              .params(org, idempotency)
              .query(new org.springframework.jdbc.core.ColumnMapRowMapper())
              .single(),
          event);
    return new Receipt(id, false);
  }

  private Receipt replay(Map<String, Object> row, Event e) {
    Instant recorded = ((java.sql.Timestamp) row.get("occurred_at")).toInstant();
    if (!row.get("subscription_id").equals(e.subscriptionId())
        || !row.get("service").equals(e.service())
        || ((Number) row.get("quantity")).longValue() != e.quantity()
        || !recorded.equals(e.occurredAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS)))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Idempotency key already used for a different event");
    return new Receipt((UUID) row.get("id"), true);
  }
}
