package com.meterflow.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/organizations/{org}/analyst")
public class AnalystController {
  private final AnalystService service;

  public AnalystController(AnalystService service) {
    this.service = service;
  }

  public record Question(@NotBlank @Size(max = 500) String question, @NotNull LocalDate month) {}

  @PostMapping
  AnalystService.Analysis analyze(@PathVariable UUID org, @Valid @RequestBody Question q) {
    return service.analyze(org, q.month(), q.question());
  }
}
