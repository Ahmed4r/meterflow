package com.meterflow.identity;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.cors.*;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  SecretKeySpec key(@Value("${meterflow.jwt-secret}") String secret) {
    if (secret.getBytes(StandardCharsets.UTF_8).length < 32)
      throw new IllegalArgumentException("JWT_SECRET must be at least 32 bytes");
    return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
  }

  @Bean
  JwtEncoder encoder(SecretKeySpec key) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(key));
  }

  @Bean
  JwtDecoder decoder(SecretKeySpec key) {
    var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("meterflow"));
    return decoder;
  }

  @Bean
  SecurityFilterChain security(
      HttpSecurity http, JdbcClient db, @Value("${meterflow.origin}") String origin)
      throws Exception {
    var cors = new CorsConfiguration();
    cors.setAllowedOrigins(List.of(origin));
    cors.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
    cors.setAllowedHeaders(
        List.of("Authorization", "Content-Type", "Idempotency-Key", "X-API-Key"));
    cors.setAllowCredentials(true);
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", cors);
    // Bearer auth has no ambient credentials. Cookie refresh/logout additionally enforce exact
    // Origin.
    http.csrf(c -> c.disable())
        .cors(c -> c.configurationSource(source))
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/api/v1/auth/**",
                        "/api/v1/usage-events",
                        "/actuator/health",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(o -> o.jwt(j -> {}));
    http.addFilterAfter(
        new OncePerRequestFilter() {
          @Override
          protected void doFilterInternal(
              jakarta.servlet.http.HttpServletRequest req,
              jakarta.servlet.http.HttpServletResponse res,
              jakarta.servlet.FilterChain chain)
              throws java.io.IOException, jakarta.servlet.ServletException {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
              boolean active = false;
              try {
                active =
                    db.sql(
                            "select exists(select 1 from auth_session where id=? and user_id=? and not revoked and expires_at>now())")
                        .params(
                            java.util.UUID.fromString(jwt.getClaimAsString("sid")),
                            java.util.UUID.fromString(jwt.getSubject()))
                        .query(Boolean.class)
                        .single();
              } catch (IllegalArgumentException ignored) {
              }
              if (!active) {
                res.setStatus(401);
                return;
              }
            }
            chain.doFilter(req, res);
          }
        },
        BasicAuthenticationFilter.class);
    return http.build();
  }
}
