package com.meterflow.billing;

import java.time.*;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BillingScheduler {
  private final BillingService billing;
  private final JdbcClient db;

  public BillingScheduler(BillingService billing, JdbcClient db) {
    this.billing = billing;
    this.db = db;
  }

  @Scheduled(cron = "0 15 * * * *", zone = "UTC")
  public void closeMonths() {
    // Bounded catch-up batch; each invoice commits independently. Failed work
    // remains eligible.
    var pending =
        db.sql(
                "select s.id,s.organization_id,m::date month from subscription s cross join lateral generate_series(date_trunc('month',s.starts_at at time zone 'UTC'),date_trunc('month',now() at time zone 'UTC')-interval '1 month',interval '1 month') m where (s.cancelled_at is null or s.cancelled_at>m at time zone 'UTC') and not exists(select 1 from invoice i where i.subscription_id=s.id and i.period_start=m::date) order by m,s.id limit 100")
            .query(new org.springframework.jdbc.core.ColumnMapRowMapper())
            .list();
    for (var item : pending)
      try {
        billing.generate(
            (UUID) item.get("organization_id"),
            (UUID) item.get("id"),
            ((java.sql.Date) item.get("month")).toLocalDate());
      } catch (Exception e) {
        org.slf4j.LoggerFactory.getLogger(getClass())
            .warn("Scheduled invoice failed: {}", e.getClass().getSimpleName());
      }
  }
}
