package com.meterflow.shared;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RetentionJob {
  private final JdbcClient db;
  private final int days;

  public RetentionJob(JdbcClient db, @Value("${meterflow.retention-days}") int days) {
    this.db = db;
    if (days < 90) throw new IllegalArgumentException("Retention must be at least 90 days");
    this.days = days;
  }

  @Scheduled(cron = "0 30 3 * * *", zone = "UTC")
  public void clean() {
    // Only delete raw usage after its invoice snapshot exists. Bound each batch.
    db.sql(
            "delete from usage_event where id in (select u.id from usage_event u where u.received_at<now()-make_interval(days=>?) and exists(select 1 from invoice i where i.subscription_id=u.subscription_id and u.occurred_at>=i.period_start::timestamp at time zone 'UTC' and u.occurred_at<i.period_end::timestamp at time zone 'UTC') limit 10000)")
        .param(days)
        .update();
    db.sql("delete from auth_session where expires_at<now()-interval '1 day'").update();
  }
}
