package com.meterflow.identity;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class TenantAccess {
  private final JdbcClient db;

  public TenantAccess(JdbcClient db) {
    this.db = db;
  }

  public UUID userId() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt))
      throw new AccessDeniedException("Authentication required");
    return UUID.fromString(jwt.getSubject());
  }

  public String require(UUID org, boolean write) {
    String role =
        db.sql("select role from membership where organization_id=? and user_id=?")
            .params(org, userId())
            .query(String.class)
            .optional()
            .orElseThrow(() -> new AccessDeniedException("Membership required"));
    if (write && role.equals("VIEWER")) throw new AccessDeniedException("Write access required");
    return role;
  }

  public void owner(UUID org) {
    if (!require(org, true).equals("OWNER")) throw new AccessDeniedException("Owner required");
  }
}
