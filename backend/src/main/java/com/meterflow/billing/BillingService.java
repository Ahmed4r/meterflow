package com.meterflow.billing;

import com.meterflow.identity.TenantAccess;
import com.meterflow.subscriptions.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BillingService {
  private final JdbcClient db;
  private final SubscriptionRepository subscriptions;
  private final CatalogRepository catalog;
  private final TenantAccess access;

  public BillingService(
      JdbcClient db,
      SubscriptionRepository subscriptions,
      CatalogRepository catalog,
      TenantAccess access) {
    this.db = db;
    this.subscriptions = subscriptions;
    this.catalog = catalog;
    this.access = access;
  }

  @Transactional
  public UUID generateAuthorized(UUID org, UUID subscription, LocalDate month) {
    access.require(org, true);
    return generate(org, subscription, month);
  }

  /** Internal scheduler entry point; never exposed directly to a controller. */
  @Transactional
  public UUID generate(UUID org, UUID subscription, LocalDate month) {
    if (month.getDayOfMonth() != 1
        || !month.isBefore(LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1)))
      throw new IllegalArgumentException("Only closed calendar months can be billed");
    var sub =
        subscriptions
            .lock(org, subscription)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found"));
    var existing =
        db.sql("select id from invoice where subscription_id=? and period_start=?")
            .params(subscription, month)
            .query(UUID.class)
            .optional();
    if (existing.isPresent()) return existing.get();
    Instant start = month.atStartOfDay(ZoneOffset.UTC).toInstant(),
        end = month.plusMonths(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    if (!sub.startsAt().isBefore(end)
        || (sub.cancelledAt() != null && !sub.cancelledAt().isAfter(start)))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Subscription not active in this month");
    var plan = catalog.plan(org, sub.planId());
    long units =
        db.sql(
                "select coalesce(sum(quantity),0) from usage_event where organization_id=? and subscription_id=? and occurred_at>=? and occurred_at<?")
            .params(org, subscription, start.atOffset(ZoneOffset.UTC), end.atOffset(ZoneOffset.UTC))
            .query(Long.class)
            .single();
    var lines = Pricing.calculate(plan.basePrice(), plan.tiers(), units);
    UUID id = UUID.randomUUID();
    db.sql(
            "insert into invoice(id,organization_id,subscription_id,period_start,period_end,currency,total,status) values (?,?,?,?,?,'EUR',?,'OPEN')")
        .params(id, org, subscription, month, month.plusMonths(1), Pricing.total(lines))
        .update();
    for (int i = 0; i < lines.size(); i++) {
      var l = lines.get(i);
      db.sql("insert into invoice_line values (?,?,?,?,?,?,?)")
          .params(
              UUID.randomUUID(), id, l.description(), l.quantity(), l.unitPrice(), l.amount(), i)
          .update();
    }
    return id;
  }

  public List<Map<String, Object>> list(UUID org, int page, String status) {
    access.require(org, false);
    return db.sql(
            "select i.id,i.subscription_id,c.name customer,i.period_start,i.period_end,i.currency,i.total,i.status,i.created_at from invoice i join subscription s on s.id=i.subscription_id join customer c on c.id=s.customer_id where i.organization_id=? and (?='' or i.status=?) order by i.period_start desc,i.id limit 50 offset ?")
        .params(org, status, status, page * 50)
        .query(new org.springframework.jdbc.core.ColumnMapRowMapper())
        .list();
  }

  public Map<String, Object> detail(UUID org, UUID id) {
    access.require(org, false);
    var result =
        new LinkedHashMap<>(
            db.sql(
                    "select id,subscription_id,period_start,period_end,currency,total,status,created_at from invoice where organization_id=? and id=?")
                .params(org, id)
                .query(new org.springframework.jdbc.core.ColumnMapRowMapper())
                .optional()
                .orElseThrow(
                    () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found")));
    result.put(
        "lines",
        db.sql(
                "select description,quantity,unit_price,amount from invoice_line where invoice_id=? order by position")
            .param(id)
            .query(new org.springframework.jdbc.core.ColumnMapRowMapper())
            .list());
    return result;
  }

  @Transactional
  public void markPaid(UUID org, UUID id) {
    access.require(org, true);
    if (db.sql("update invoice set status='PAID' where organization_id=? and id=?")
            .params(org, id)
            .update()
        == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found");
  }
}
