package com.meterflow.analytics;

import com.meterflow.billing.Pricing;
import com.meterflow.identity.TenantAccess;
import com.meterflow.subscriptions.CatalogRepository;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsService {
  private final JdbcClient db;
  private final TenantAccess access;
  private final CatalogRepository catalog;

  public AnalyticsService(JdbcClient db, TenantAccess access, CatalogRepository catalog) {
    this.db = db;
    this.access = access;
    this.catalog = catalog;
  }

  public record Day(LocalDate date, long units) {}

  public record ServiceUsage(String service, long units) {}

  public record Summary(
      String month,
      String currency,
      long totalUsage,
      BigDecimal estimatedBill,
      long previousUsage,
      BigDecimal previousBill,
      BigDecimal usageChangePercent,
      long activeSubscriptions,
      List<Day> daily,
      List<ServiceUsage> services,
      List<Day> anomalies) {}

  private OffsetDateTime start(LocalDate month) {
    return month.atStartOfDay().atOffset(ZoneOffset.UTC);
  }

  private void validate(LocalDate month) {
    if (month.getDayOfMonth() != 1
        || month.isAfter(LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1))
        || month.isBefore(LocalDate.now(ZoneOffset.UTC).minusYears(5)))
      throw new IllegalArgumentException("Invalid month");
  }

  public List<ServiceUsage> getUsageByService(UUID org, LocalDate month) {
    access.require(org, false);
    validate(month);
    return db.sql(
            "select service,sum(quantity)::bigint units from usage_event where organization_id=? and occurred_at>=? and occurred_at<? group by service order by units desc")
        .params(org, start(month), start(month.plusMonths(1)))
        .query(ServiceUsage.class)
        .list();
  }

  public List<Day> daily(UUID org, LocalDate month) {
    access.require(org, false);
    validate(month);
    var rows =
        db.sql(
                "select (occurred_at at time zone 'UTC')::date date,sum(quantity)::bigint units from usage_event where organization_id=? and occurred_at>=? and occurred_at<? group by 1 order by 1")
            .params(org, start(month), start(month.plusMonths(1)))
            .query(Day.class)
            .list();
    Map<LocalDate, Long> map = new HashMap<>();
    rows.forEach(d -> map.put(d.date(), d.units()));
    int count =
        month.equals(LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1))
            ? LocalDate.now(ZoneOffset.UTC).getDayOfMonth()
            : month.lengthOfMonth();
    List<Day> days = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      var date = month.plusDays(i);
      days.add(new Day(date, map.getOrDefault(date, 0L)));
    }
    return days;
  }

  public List<Day> detectUsageAnomalies(UUID org, LocalDate month) {
    access.require(org, false);
    var days = daily(org, month);
    if (days.size() < 7) return List.of();
    BigDecimal mean =
        BigDecimal.valueOf(days.stream().mapToLong(Day::units).sum())
            .divide(BigDecimal.valueOf(days.size()), 6, RoundingMode.HALF_UP);
    return days.stream()
        .filter(
            d ->
                d.units() > 100
                    && BigDecimal.valueOf(d.units()).compareTo(mean.multiply(BigDecimal.valueOf(2)))
                        > 0)
        .toList();
  }

  public BigDecimal estimatePlanCost(UUID org, UUID plan, long units) {
    access.require(org, false);
    if (units < 0) throw new IllegalArgumentException();
    var p = catalog.plan(org, plan);
    return Pricing.total(Pricing.calculate(p.basePrice(), p.tiers(), units));
  }

  private BigDecimal cost(UUID org, LocalDate month) {
    var subs =
        db.sql(
                "select s.id,s.plan_id,coalesce(sum(u.quantity),0)::bigint units from subscription s left join usage_event u on u.subscription_id=s.id and u.occurred_at>=? and u.occurred_at<? where s.organization_id=? and s.starts_at<? and (s.cancelled_at is null or s.cancelled_at>?) group by s.id,s.plan_id")
            .params(
                start(month),
                start(month.plusMonths(1)),
                org,
                start(month.plusMonths(1)),
                start(month))
            .query(new org.springframework.jdbc.core.ColumnMapRowMapper())
            .list();
    BigDecimal total = new BigDecimal("0.00");
    for (var sub : subs) {
      var issued =
          db.sql("select total from invoice where subscription_id=? and period_start=?")
              .params(sub.get("id"), month)
              .query(BigDecimal.class)
              .optional();
      total =
          total.add(
              issued.orElseGet(
                  () ->
                      estimatePlanCost(
                          org,
                          (UUID) sub.get("plan_id"),
                          ((Number) sub.get("units")).longValue())));
    }
    return total;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Summary getBillingSummary(UUID org, LocalDate month) {
    access.require(org, false);
    validate(month);
    var daily = daily(org, month);
    var previous = daily(org, month.minusMonths(1));
    long units = daily.stream().mapToLong(Day::units).sum(),
        prior = previous.stream().mapToLong(Day::units).sum();
    BigDecimal change =
        prior == 0
            ? null
            : BigDecimal.valueOf(units)
                .subtract(BigDecimal.valueOf(prior))
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(prior), 1, RoundingMode.HALF_UP);
    long active =
        db.sql("select count(*) from subscription where organization_id=? and status='ACTIVE'")
            .param(org)
            .query(Long.class)
            .single();
    return new Summary(
        month.toString(),
        "EUR",
        units,
        cost(org, month),
        prior,
        cost(org, month.minusMonths(1)),
        change,
        active,
        daily,
        getUsageByService(org, month),
        detectUsageAnomalies(org, month));
  }

  public Summary compareBillingPeriods(UUID org, LocalDate month) {
    access.require(org, false);
    return getBillingSummary(org, month);
  }
}
