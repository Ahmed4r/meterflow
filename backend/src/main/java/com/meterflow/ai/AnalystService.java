package com.meterflow.ai;

import com.meterflow.analytics.AnalyticsService;
import com.meterflow.identity.TenantAccess;
import com.meterflow.metering.RateLimiter;
import com.meterflow.subscriptions.CatalogRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class AnalystService {
  private final TenantAccess access;
  private final AnalyticsService analytics;
  private final CatalogRepository catalog;
  private final JdbcClient db;
  private final AnalystProvider provider;
  private final RateLimiter limiter;

  public AnalystService(
      TenantAccess access,
      AnalyticsService analytics,
      CatalogRepository catalog,
      JdbcClient db,
      AnalystProvider provider,
      RateLimiter limiter) {
    this.access = access;
    this.analytics = analytics;
    this.catalog = catalog;
    this.db = db;
    this.provider = provider;
    this.limiter = limiter;
  }

  public record PlanCost(String plan, BigDecimal amount) {}

  public record Analysis(
      String mode,
      String explanation,
      AnalyticsService.Summary facts,
      List<PlanCost> recommendations) {}

  public Analysis analyze(UUID org, LocalDate month, String question) {
    access.require(org, false);
    limiter.require("analyst:" + org, 15);
    var facts = analytics.getBillingSummary(org, month);
    var services = facts.services();
    var anomalies = facts.anomalies();
    Set<String> intents = intents(question);

    List<PlanCost> costs =
        catalog.plans(org).stream()
            .map(
                p ->
                    new PlanCost(
                        p.name(), analytics.estimatePlanCost(org, p.id(), facts.totalUsage())))
            .sorted(Comparator.comparing(PlanCost::amount))
            .toList();

    boolean currentMonth = month.equals(LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1));
    Map<String, String> evidence = new LinkedHashMap<>();
    evidence.put(
        "comparison",
        "Selected period usage is "
            + facts.totalUsage()
            + " units and its bill is EUR "
            + facts.estimatedBill()
            + ". Previous period usage was "
            + facts.previousUsage()
            + " units and its bill was EUR "
            + facts.previousBill()
            + ".");
    evidence.put(
        "period",
        currentMonth
            ? "The selected current month is month-to-date, while the previous month is complete. "
                + "These totals are not directly comparable as full months. Monthly platform fees "
                + "are charged in full without proration."
            : "Both selected billing periods are complete calendar months in UTC.");
    if (!services.isEmpty()) {
      var top = services.getFirst();
      // Do not send user-controlled service names to the provider.
      evidence.put(
          "service",
          "The highest-volume service consumed "
              + top.units()
              + " units in the selected period. Service volume alone does not establish "
              + "its share of graduated billing costs.");
    }
    evidence.put(
        "anomalies",
        anomalies.isEmpty()
            ? "No days exceeded the usage spike heuristic. This does not rule out other anomalies."
            : anomalies.size()
                + " day(s) exceeded the usage spike heuristic. "
                + "A spike does not establish an error or its cause.");
    if (!anomalies.isEmpty()) {
      var highest =
          anomalies.stream()
              .max(Comparator.comparingLong(AnalyticsService.Day::units))
              .orElseThrow();
      evidence.put(
          "spike_detail",
          "The highest flagged day was "
              + highest.date()
              + " with "
              + highest.units()
              + " units. No cause has been established.");
    }
    if (costs.size() >= 2) {
      var cheapest = costs.getFirst();
      var next = costs.get(1);
      evidence.put(
          "plans",
          "For an illustrative single subscription with "
              + facts.totalUsage()
              + " units, the lowest modeled plan costs EUR "
              + cheapest.amount()
              + " and the next lowest costs EUR "
              + next.amount()
              + ". This is not a guaranteed organization-wide saving; tiers apply per subscription.");
    } else if (costs.size() == 1) {
      evidence.put(
          "plans",
          "One plan has an illustrative single-subscription cost of EUR "
              + costs.getFirst().amount()
              + ". No alternative plan was available to compare.");
    }

    Map<String, Object> aggregates = new LinkedHashMap<>();
    aggregates.put("currency", "EUR");
    aggregates.put("usage", facts.totalUsage());
    aggregates.put("previousUsage", facts.previousUsage());
    aggregates.put("estimatedBill", facts.estimatedBill());
    aggregates.put("previousBill", facts.previousBill());
    aggregates.put("anomalyDays", anomalies.size());
    aggregates.put("currentMonthToDate", currentMonth);

    boolean enabled =
        db.sql("select ai_enabled from organization where id=?")
            .param(org)
            .query(Boolean.class)
            .single();
    if (enabled && provider.configured()) {
      var ai = provider.explain(intents, evidence, aggregates);
      if (ai.isPresent()) return new Analysis("AI", ai.get(), facts, costs);
    }

    List<String> wanted = new ArrayList<>();
    if (intents.contains("COMPARISON")) {
      wanted.add("comparison");
      wanted.add("period");
    }
    if (intents.contains("ANOMALIES")) {
      wanted.add("anomalies");
      wanted.add("spike_detail");
    }
    if (intents.contains("SERVICE")) wanted.add("service");
    if (intents.contains("PLANS") || intents.contains("SAVINGS")) wanted.add("plans");
    if (currentMonth) wanted.add("period");
    String fallback =
        wanted.stream()
            .distinct()
            .filter(evidence::containsKey)
            .map(evidence::get)
            .collect(Collectors.joining("\n\n"));
    if (fallback.isBlank()) fallback = evidence.get("comparison");
    return new Analysis("DETERMINISTIC", fallback, facts, costs);
  }

  static Set<String> intents(String question) {
    String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
    Set<String> result = new LinkedHashSet<>();
    if (hasAny(q, "bill", "cost", "compare", "month", "increase", "decrease", "higher", "lower"))
      result.add("COMPARISON");
    if (hasAny(q, "spike", "unusual", "anomal", "sudden", "surge")) result.add("ANOMALIES");
    if (hasAny(q, "plan", "tier", "cost-effective")) result.add("PLANS");
    if (hasAny(q, "reduce", "save", "saving", "optimiz")) result.add("SAVINGS");
    if (hasAny(q, "service", "most", "api", "driver")) result.add("SERVICE");
    if (result.isEmpty()) result.add("COMPARISON");
    return Collections.unmodifiableSet(result);
  }

  private static boolean hasAny(String text, String... fragments) {
    for (String fragment : fragments) if (text.contains(fragment)) return true;
    return false;
  }
}
