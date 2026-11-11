package com.meterflow.metering;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "apiKey")
public class UsageController {
  private final UsageService service;

  public UsageController(UsageService service) {
    this.service = service;
  }

  @PostMapping("/api/v1/usage-events")
  UsageService.Receipt ingest(
      @RequestHeader("X-API-Key") String key,
      @RequestHeader("Idempotency-Key") String idempotency,
      @Valid @RequestBody UsageService.Event event) {
    return service.ingest(key, idempotency, event);
  }
}
