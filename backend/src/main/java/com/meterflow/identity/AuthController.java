package com.meterflow.identity;

import com.meterflow.metering.RateLimiter;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@io.swagger.v3.oas.annotations.security.SecurityRequirements
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final AuthService service;
  private final RateLimiter limiter;
  private final String origin;
  private final boolean secure;

  public AuthController(
      AuthService service,
      RateLimiter limiter,
      @Value("${meterflow.origin}") String origin,
      @Value("${meterflow.secure-cookie}") boolean secure) {
    this.service = service;
    this.limiter = limiter;
    this.origin = origin;
    this.secure = secure;
  }

  public record Register(
      @Email @NotBlank @Size(max = 254) String email,
      @NotBlank @Size(min = 10, max = 72) String password,
      @NotBlank @Size(max = 100) String name,
      @NotBlank @Size(max = 100) String organization) {}

  public record Login(
      @Email @NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 72) String password) {}

  @PostMapping("/register")
  public Map<String, String> register(
      @Valid @RequestBody Register input, HttpServletRequest req, HttpServletResponse res) {
    limit(req);
    return result(
        service.register(input.email(), input.password(), input.name(), input.organization()), res);
  }

  @PostMapping("/login")
  public Map<String, String> login(
      @Valid @RequestBody Login input, HttpServletRequest req, HttpServletResponse res) {
    limit(req);
    limiter.require(
        "login:" + AuthService.hash(input.email().trim().toLowerCase(java.util.Locale.ROOT)), 10);
    return result(service.login(input.email(), input.password()), res);
  }

  @PostMapping("/refresh")
  public Map<String, String> refresh(
      @CookieValue(name = "mf_refresh", defaultValue = "") String token,
      HttpServletRequest req,
      HttpServletResponse res) {
    checkOrigin(req);
    limit(req);
    return result(service.refresh(token), res);
  }

  @PostMapping("/logout")
  public void logout(
      @CookieValue(name = "mf_refresh", defaultValue = "") String token,
      HttpServletRequest req,
      HttpServletResponse res) {
    checkOrigin(req);
    service.logout(token);
    res.addHeader(HttpHeaders.SET_COOKIE, cookie("", 0));
  }

  private void limit(HttpServletRequest req) {
    limiter.require("auth:" + AuthService.hash(req.getRemoteAddr()), 120);
  }

  private void checkOrigin(HttpServletRequest req) {
    if (!origin.equals(req.getHeader("Origin")))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Origin not allowed");
  }

  private Map<String, String> result(AuthService.Tokens tokens, HttpServletResponse res) {
    res.addHeader(HttpHeaders.SET_COOKIE, cookie(tokens.refreshToken(), 14 * 86400));
    res.setHeader("Cache-Control", "no-store");
    return Map.of("accessToken", tokens.accessToken());
  }

  private String cookie(String value, long age) {
    return ResponseCookie.from("mf_refresh", value)
        .httpOnly(true)
        .secure(secure)
        .sameSite("Strict")
        .path("/api/v1/auth")
        .maxAge(age)
        .build()
        .toString();
  }
}
