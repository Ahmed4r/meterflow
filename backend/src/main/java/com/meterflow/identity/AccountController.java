package com.meterflow.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AccountController {
  private final AccountService service;

  public AccountController(AccountService service) {
    this.service = service;
  }

  public record Name(@NotBlank @Size(max = 100) String name) {}

  public record Delete(@NotBlank @Size(max = 72) String password) {}

  public record Settings(@NotBlank @Size(max = 100) String name, boolean aiEnabled) {}

  public record Member(
      @Email @NotBlank String email,
      @Pattern(regexp = "OWNER|ADMIN|VIEWER") @NotNull String role) {}

  @GetMapping("/me")
  Object me() {
    return service.profile();
  }

  @PatchMapping("/me")
  void rename(@Valid @RequestBody Name i) {
    service.rename(i.name());
  }

  @GetMapping("/me/export")
  ResponseEntity<Object> export() {
    return ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=meterflow-personal-data.json")
        .cacheControl(CacheControl.noStore())
        .body(service.export());
  }

  @DeleteMapping("/me")
  void delete(@Valid @RequestBody Delete i) {
    service.delete(i.password());
  }

  @GetMapping("/organizations/{org}/members")
  Object members(@PathVariable UUID org) {
    return service.members(org);
  }

  @PostMapping("/organizations/{org}/members")
  void member(@PathVariable UUID org, @Valid @RequestBody Member i) {
    service.member(org, i.email(), i.role());
  }

  @PatchMapping("/organizations/{org}")
  void settings(@PathVariable UUID org, @Valid @RequestBody Settings i) {
    service.settings(org, i.name(), i.aiEnabled());
  }

  @GetMapping("/organizations/{org}/api-keys")
  Object keys(@PathVariable UUID org) {
    return service.keys(org);
  }

  @PostMapping("/organizations/{org}/api-keys")
  Object key(@PathVariable UUID org, @Valid @RequestBody Name i) {
    if (i.name().length() > 80) throw new IllegalArgumentException();
    return service.key(org, i.name());
  }

  @DeleteMapping("/organizations/{org}/api-keys/{id}")
  void revoke(@PathVariable UUID org, @PathVariable UUID id) {
    service.revoke(org, id);
  }
}
