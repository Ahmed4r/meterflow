package com.meterflow.billing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/v1/organizations/{org}/invoices")
public class BillingController {
  private final BillingService service;
  private final InvoicePdf pdf;

  public BillingController(BillingService service, InvoicePdf pdf) {
    this.service = service;
    this.pdf = pdf;
  }

  public record Generate(@NotNull UUID subscriptionId, @NotNull LocalDate month) {}

  @GetMapping
  Object list(
      @PathVariable UUID org,
      @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
      @RequestParam(defaultValue = "") @Pattern(regexp = "|OPEN|PAID") String status) {
    return service.list(org, page, status);
  }

  @PostMapping
  Object generate(@PathVariable UUID org, @Valid @RequestBody Generate input) {
    return Map.of("id", service.generateAuthorized(org, input.subscriptionId(), input.month()));
  }

  @GetMapping("/{id}")
  Object detail(@PathVariable UUID org, @PathVariable UUID id) {
    return service.detail(org, id);
  }

  @PostMapping("/{id}/paid")
  void paid(@PathVariable UUID org, @PathVariable UUID id) {
    service.markPaid(org, id);
  }

  @GetMapping(value = "/{id}/pdf", produces = "application/pdf")
  ResponseEntity<byte[]> pdf(@PathVariable UUID org, @PathVariable UUID id)
      throws java.io.IOException {
    return ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=invoice-" + id + ".pdf")
        .cacheControl(CacheControl.noStore())
        .body(pdf.render(org, id));
  }
}
