package com.meterflow.subscriptions;

import com.meterflow.billing.Pricing;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/v1/organizations/{org}")
public class CatalogController {
  private final CatalogService service;

  public CatalogController(CatalogService service) {
    this.service = service;
  }

  public record PlanInput(
      @NotBlank @Size(max = 80) String name,
      @NotNull @DecimalMin("0") @DecimalMax("1000000") @Digits(integer = 7, fraction = 2)
          BigDecimal basePrice,
      @NotEmpty @Size(max = 20) List<Pricing.Tier> tiers) {}

  public record CustomerInput(
      @NotBlank @Size(max = 100) String name, @NotBlank @Size(max = 100) String reference) {}

  public record SubscribeInput(@NotNull UUID customerId, @NotNull UUID planId) {}

  @GetMapping("/plans")
  Object plans(@PathVariable UUID org) {
    return service.plans(org);
  }

  @PostMapping("/plans")
  Object plan(@PathVariable UUID org, @Valid @RequestBody PlanInput i) {
    return Map.of("id", service.plan(org, i.name(), i.basePrice(), i.tiers()));
  }

  @GetMapping("/customers")
  Object customers(
      @PathVariable UUID org,
      @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
      @RequestParam(defaultValue = "") @Size(max = 100) String search) {
    return service.customers(org, page, search);
  }

  @PostMapping("/customers")
  Object customer(@PathVariable UUID org, @Valid @RequestBody CustomerInput i) {
    return Map.of("id", service.customer(org, i.name(), i.reference()));
  }

  @GetMapping("/subscriptions")
  Object subscriptions(
      @PathVariable UUID org, @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page) {
    return service.subscriptions(org, page);
  }

  @PostMapping("/subscriptions")
  Object subscribe(@PathVariable UUID org, @Valid @RequestBody SubscribeInput i) {
    return Map.of("id", service.subscribe(org, i.customerId(), i.planId()));
  }

  @PostMapping("/subscriptions/{id}/cancel")
  void cancel(@PathVariable UUID org, @PathVariable UUID id) {
    service.cancel(org, id);
  }

  @GetMapping("/subscriptions/{id}/history")
  Object history(@PathVariable UUID org, @PathVariable UUID id) {
    return service.history(org, id);
  }
}
