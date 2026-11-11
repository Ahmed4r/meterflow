package com.meterflow.identity;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
  private final IdentityRepository users;
  private final PasswordEncoder passwords;
  private final JdbcClient db;
  private final JwtEncoder encoder;
  private final String dummyHash;

  public AuthService(
      IdentityRepository users, PasswordEncoder passwords, JdbcClient db, JwtEncoder encoder) {
    this.users = users;
    this.passwords = passwords;
    this.db = db;
    this.encoder = encoder;
    dummyHash = passwords.encode("timing-only-unused-password");
  }

  public record Tokens(String accessToken, String refreshToken) {}

  public static String randomToken() {
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  @Transactional
  public Tokens register(String email, String password, String name, String orgName) {
    if (password.getBytes(StandardCharsets.UTF_8).length > 72)
      throw new IllegalArgumentException("Password too long");
    UUID user = UUID.randomUUID(), org = UUID.randomUUID();
    users.create(
        user, email.toLowerCase(Locale.ROOT).trim(), passwords.encode(password), name.trim());
    db.sql("insert into organization(id,name) values (?,?)").params(org, orgName.trim()).update();
    db.sql("insert into membership values (?,?,'OWNER')").params(org, user).update();
    return session(user);
  }

  @Transactional
  public Tokens login(String email, String password) {
    var user = users.byEmail(email.toLowerCase(Locale.ROOT).trim());
    boolean matches =
        passwords.matches(
            password, user.map(IdentityRepository.User::passwordHash).orElse(dummyHash));
    if (user.isEmpty() || !matches)
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    return session(user.get().id());
  }

  private Tokens session(UUID user) {
    UUID sid = UUID.randomUUID();
    String refresh = randomToken();
    db.sql("insert into auth_session(id,user_id,refresh_hash,expires_at) values (?,?,?,?)")
        .params(sid, user, hash(refresh), OffsetDateTime.now(ZoneOffset.UTC).plusDays(14))
        .update();
    return new Tokens(access(user, sid), refresh);
  }

  private String access(UUID user, UUID sid) {
    Instant now = Instant.now();
    var claims =
        JwtClaimsSet.builder()
            .issuer("meterflow")
            .subject(user.toString())
            .issuedAt(now)
            .expiresAt(now.plusSeconds(900))
            .claim("sid", sid.toString())
            .build();
    return encoder
        .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
  }

  @Transactional(noRollbackFor = RefreshReuseException.class)
  public Tokens refresh(String token) {
    var session =
        db.sql(
                "select id,user_id from auth_session where refresh_hash=? and not revoked and expires_at>now() for update")
            .param(hash(token))
            .query(new org.springframework.jdbc.core.ColumnMapRowMapper())
            .optional();
    if (session.isEmpty()) {
      var usedSession =
          db.sql("select session_id from refresh_token_history where token_hash=?")
              .param(hash(token))
              .query(UUID.class)
              .optional();
      if (usedSession.isPresent()) {
        // This security mutation must commit even though the HTTP response is 401.
        db.sql("update auth_session set revoked=true where id=?").param(usedSession.get()).update();
        throw new RefreshReuseException();
      }
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expired");
    }
    String next = randomToken();
    UUID sid = (UUID) session.get().get("id"), user = (UUID) session.get().get("user_id");
    db.sql("insert into refresh_token_history(token_hash,session_id) values (?,?)")
        .params(hash(token), sid)
        .update();
    db.sql("update auth_session set refresh_hash=? where id=?").params(hash(next), sid).update();
    return new Tokens(access(user, sid), next);
  }

  public static final class RefreshReuseException extends ResponseStatusException {
    public RefreshReuseException() {
      super(HttpStatus.UNAUTHORIZED, "Session revoked. Sign in again.");
    }
  }

  @Transactional
  public void logout(String token) {
    db.sql("update auth_session set revoked=true where refresh_hash=?").param(hash(token)).update();
  }
}
