package com.meterflow.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Graduated pricing. Upper bounds are cumulative; final tier is unbounded. */
public final class Pricing {
  private Pricing() {}

  public record Tier(Long upTo, BigDecimal unitPrice) {}

  public record Line(String description, long quantity, BigDecimal unitPrice, BigDecimal amount) {}

  public static void validate(List<Tier> tiers) {
    if (tiers == null || tiers.isEmpty() || tiers.size() > 20)
      throw new IllegalArgumentException("Provide 1–20 tiers");
    long previous = 0;
    for (int i = 0; i < tiers.size(); i++) {
      var t = tiers.get(i);
      if (t == null
          || t.unitPrice() == null
          || t.unitPrice().signum() < 0
          || t.unitPrice().scale() > 6
          || t.unitPrice().compareTo(new BigDecimal("10000")) > 0)
        throw new IllegalArgumentException("Invalid unit price");
      if (t.upTo() == null) {
        if (i != tiers.size() - 1)
          throw new IllegalArgumentException("Only the final tier is unbounded");
      } else {
        if (t.upTo() <= previous || i == tiers.size() - 1)
          throw new IllegalArgumentException(
              "Increasing bounds and an unbounded final tier required");
        previous = t.upTo();
      }
    }
  }

  public static List<Line> calculate(BigDecimal base, List<Tier> tiers, long units) {
    validate(tiers);
    if (units < 0 || base == null || base.signum() < 0)
      throw new IllegalArgumentException("Negative amount");
    List<Line> lines = new ArrayList<>();
    lines.add(new Line("Monthly platform fee", 1, base, base.setScale(2, RoundingMode.HALF_UP)));
    long remaining = units, previous = 0;
    for (int i = 0; i < tiers.size(); i++) {
      var tier = tiers.get(i);
      long quantity = tier.upTo() == null ? remaining : Math.min(remaining, tier.upTo() - previous);
      if (quantity > 0)
        lines.add(
            new Line(
                "Usage tier " + (i + 1),
                quantity,
                tier.unitPrice(),
                tier.unitPrice()
                    .multiply(BigDecimal.valueOf(quantity))
                    .setScale(2, RoundingMode.HALF_UP)));
      remaining -= quantity;
      if (remaining == 0) break;
      previous = tier.upTo();
    }
    return List.copyOf(lines);
  }

  public static BigDecimal total(List<Line> lines) {
    return lines.stream().map(Line::amount).reduce(new BigDecimal("0.00"), BigDecimal::add);
  }
}
