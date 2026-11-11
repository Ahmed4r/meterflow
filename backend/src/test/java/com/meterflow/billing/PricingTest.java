package com.meterflow.billing;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PricingTest {
  private final List<Pricing.Tier> tiers =
      List.of(
          new Pricing.Tier(1000L, new BigDecimal("0.01")),
          new Pricing.Tier(null, new BigDecimal("0.005")));

  @Test
  void graduatedPriceAndBoundary() {
    assertThat(Pricing.total(Pricing.calculate(new BigDecimal("29"), tiers, 1500)))
        .isEqualByComparingTo("41.50");
    assertThat(Pricing.total(Pricing.calculate(BigDecimal.ZERO, tiers, 1000)))
        .isEqualByComparingTo("10.00");
  }

  @Test
  void roundsEachLineHalfUp() {
    assertThat(
            Pricing.total(
                Pricing.calculate(
                    BigDecimal.ZERO, List.of(new Pricing.Tier(null, new BigDecimal("0.005"))), 1)))
        .isEqualByComparingTo("0.01");
  }

  @Test
  void zeroUsageStillHasBaseFee() {
    assertThat(Pricing.calculate(new BigDecimal("29"), tiers, 0)).hasSize(1);
  }

  @Test
  void rejectsInvalidTiersAndNegativeUnits() {
    assertThatThrownBy(() -> Pricing.validate(java.util.Arrays.asList((Pricing.Tier) null)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Pricing.calculate(BigDecimal.ZERO, tiers, -1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Pricing.validate(List.of(new Pricing.Tier(10L, BigDecimal.ONE))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                Pricing.validate(
                    List.of(
                        new Pricing.Tier(null, BigDecimal.ONE),
                        new Pricing.Tier(null, BigDecimal.ONE))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void preservesAllUnitsAndMonotonicChargesAcrossTierBoundaries() {
    BigDecimal previous = BigDecimal.ZERO;
    for (long units : new long[] {0, 1, 999, 1000, 1001, 1002, 1500, 1000000000L}) {
      var lines = Pricing.calculate(BigDecimal.ZERO, tiers, units);
      assertThat(lines.stream().skip(1).mapToLong(Pricing.Line::quantity).sum()).isEqualTo(units);
      BigDecimal total = Pricing.total(lines);
      assertThat(total).isGreaterThanOrEqualTo(previous);
      assertThat(total.scale()).isEqualTo(2);
      previous = total;
    }
  }
}
