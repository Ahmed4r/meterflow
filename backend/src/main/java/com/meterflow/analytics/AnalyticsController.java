package com.meterflow.analytics;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/v1/organizations/{org}/analytics")
public class AnalyticsController {
  private final AnalyticsService service;

  public AnalyticsController(AnalyticsService service) {
    this.service = service;
  }

  @GetMapping
  Object summary(@PathVariable UUID org, @RequestParam LocalDate month) {
    return service.getBillingSummary(org, month);
  }

  @GetMapping("/estimate")
  Object estimate(
      @PathVariable UUID org,
      @RequestParam UUID planId,
      @RequestParam @Min(0) @Max(1000000000000L) long units) {
    return Map.of("currency", "EUR", "amount", service.estimatePlanCost(org, planId, units));
  }
}
