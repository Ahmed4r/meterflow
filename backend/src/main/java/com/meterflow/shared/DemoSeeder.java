package com.meterflow.shared;

import com.meterflow.billing.*;
import com.meterflow.identity.*;
import com.meterflow.subscriptions.CatalogRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "meterflow.demo", havingValue = "true")
public class DemoSeeder implements ApplicationRunner {
  private final AuthService auth;
  private final JdbcClient db;
  private final CatalogRepository catalog;
  private final BillingService billing;
  private final String password;

  public DemoSeeder(
      AuthService auth,
      JdbcClient db,
      CatalogRepository catalog,
      BillingService billing,
      @Value("${DEMO_PASSWORD}") String password) {
    this.auth = auth;
    this.db = db;
    this.catalog = catalog;
    this.billing = billing;
    this.password = password;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (db.sql("select exists(select 1 from app_user where email='demo@meterflow.test')")
        .query(Boolean.class)
        .single()) return;
    if (password.length() < 12)
      throw new IllegalArgumentException("DEMO_PASSWORD requires 12 characters");
    var tokens = auth.register("demo@meterflow.test", password, "Alex Morgan", "Northstar Studio");
    auth.logout(tokens.refreshToken());
    UUID org =
        db.sql(
                "select organization_id from membership m join app_user u on u.id=m.user_id where u.email='demo@meterflow.test'")
            .query(UUID.class)
            .single();
    UUID starter =
        catalog.createPlan(
            org,
            "Launch",
            new BigDecimal("29"),
            List.of(
                new Pricing.Tier(10000L, new BigDecimal("0.008")),
                new Pricing.Tier(null, new BigDecimal("0.005"))));
    UUID growth =
        catalog.createPlan(
            org,
            "Scale",
            new BigDecimal("99"),
            List.of(
                new Pricing.Tier(50000L, new BigDecimal("0.004")),
                new Pricing.Tier(null, new BigDecimal("0.0025"))));
    String[] names = {
      "Acme Labs", "Forma Studio", "Orbit Systems", "Cedar Cloud", "Mosaic Works", "Atlas Digital"
    };
    String[] services = {"Text generation", "Document parsing", "Search API", "Embeddings"};
    LocalDate first = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).minusMonths(3);
    Random random = new Random(42);
    for (int c = 0; c < names.length; c++) {
      UUID customer = catalog.createCustomer(org, names[c], "CUS-" + (1001 + c)),
          sub = UUID.randomUUID();
      db.sql(
              "insert into subscription(id,organization_id,customer_id,plan_id,status,starts_at) values (?,?,?,?,'ACTIVE',?)")
          .params(
              sub,
              org,
              customer,
              c < 3 ? growth : starter,
              first.atStartOfDay().atOffset(ZoneOffset.UTC))
          .update();
      db.sql("insert into subscription_history values (?,?,'CREATED',?)")
          .params(UUID.randomUUID(), sub, first.atStartOfDay().atOffset(ZoneOffset.UTC))
          .update();
      for (LocalDate date = first;
          !date.isAfter(LocalDate.now(ZoneOffset.UTC));
          date = date.plusDays(1)) {
        for (int s = 0; s < services.length; s++) {
          long qty = (c < 3 ? 800 : 160) + random.nextInt(300);
          qty = qty * (4 - s) / 3;
          if (date.getDayOfMonth() == 6 && s == 0) qty *= 5;
          db.sql(
                  "insert into usage_event(id,organization_id,subscription_id,idempotency_key,service,quantity,occurred_at) values (?,?,?,?,?,?,?)")
              .params(
                  UUID.randomUUID(),
                  org,
                  sub,
                  "seed-" + c + "-" + s + "-" + date,
                  services[s].toLowerCase(Locale.ROOT).replace(' ', '-'),
                  qty,
                  date.atStartOfDay().atOffset(ZoneOffset.UTC))
              .update();
        }
      }
      for (int m = 0; m < 3; m++) {
        UUID invoice = billing.generate(org, sub, first.plusMonths(m));
        if (m < 2 || c > 1)
          db.sql("update invoice set status='PAID' where id=?").param(invoice).update();
      }
    }
  }
}
