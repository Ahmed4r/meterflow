package com.meterflow.identity;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountService {
  private final JdbcClient db;
  private final TenantAccess access;
  private final IdentityRepository users;
  private final PasswordEncoder passwords;

  public AccountService(
      JdbcClient db, TenantAccess access, IdentityRepository users, PasswordEncoder passwords) {
    this.db = db;
    this.access = access;
    this.users = users;
    this.passwords = passwords;
  }

  public Map<String, Object> profile() {
    var user = users.byId(access.userId()).orElseThrow();
    return Map.of(
        "id",
        user.id(),
        "name",
        user.name(),
        "email",
        user.email(),
        "organizations",
        db.sql(
                "select o.id,o.name,o.ai_enabled,m.role from organization o join membership m on m.organization_id=o.id where m.user_id=? order by o.created_at")
            .param(user.id())
            .query(new org.springframework.jdbc.core.ColumnMapRowMapper())
            .list());
  }

  @Transactional
  public void rename(String name) {
    db.sql("update app_user set name=? where id=?").params(name, access.userId()).update();
  }

  public Map<String, Object> export() {
    var data = new LinkedHashMap<>(profile());
    data.put(
        "sessions",
        db.sql("select id,expires_at,revoked from auth_session where user_id=?")
            .param(access.userId())
            .query(new org.springframework.jdbc.core.ColumnMapRowMapper())
            .list());
    return data;
  }

  @Transactional
  public void delete(String password) {
    UUID user = access.userId();
    var identity = users.byId(user).orElseThrow();
    if (!passwords.matches(password, identity.passwordHash()))
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Password does not match");
    // Serialize owner membership changes with organization-row locks.
    var owned =
        db.sql(
                "select o.id from organization o join membership m on m.organization_id=o.id where m.user_id=? and m.role='OWNER' order by o.id for update of o")
            .param(user)
            .query(UUID.class)
            .list();
    for (UUID org : owned) {
      int other =
          db.sql("select count(*) from membership where organization_id=? and user_id<>?")
              .params(org, user)
              .query(Integer.class)
              .single();
      int owners =
          db.sql(
                  "select count(*) from membership where organization_id=? and user_id<>? and role='OWNER'")
              .params(org, user)
              .query(Integer.class)
              .single();
      if (other > 0 && owners == 0)
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "Assign another owner before deleting your account");
      if (other == 0) {
        db.sql("update api_key set revoked=true where organization_id=?").param(org).update();
        db.sql("update organization set name='Deleted organization',ai_enabled=false where id=?")
            .param(org)
            .update();
        db.sql(
                "update subscription set status='CANCELLED',cancelled_at=coalesce(cancelled_at,now()),version=version+1 where organization_id=?")
            .param(org)
            .update();
        db.sql(
                "update customer set name='Deleted customer',reference=id::text where organization_id=?")
            .param(org)
            .update();
      }
    }
    db.sql("delete from app_user where id=?").param(user).update();
  }

  public List<Map<String, Object>> members(UUID org) {
    access.require(org, false);
    return db.sql(
            "select u.id,u.name,u.email,m.role from membership m join app_user u on u.id=m.user_id where organization_id=? order by u.name")
        .param(org)
        .query(new org.springframework.jdbc.core.ColumnMapRowMapper())
        .list();
  }

  @Transactional
  public void member(UUID org, String email, String role) {
    access.owner(org);
    db.sql("select id from organization where id=? for update")
        .param(org)
        .query(UUID.class)
        .single();
    var user =
        users
            .byEmail(email.toLowerCase(Locale.ROOT))
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User must register before being added"));
    if (user.id().equals(access.userId()))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot change your own role");
    db.sql(
            "insert into membership values (?,?,?) on conflict(organization_id,user_id) do update set role=excluded.role")
        .params(org, user.id(), role)
        .update();
  }

  @Transactional
  public void settings(UUID org, String name, boolean ai) {
    access.require(org, true);
    db.sql("update organization set name=?,ai_enabled=? where id=?").params(name, ai, org).update();
  }

  public List<Map<String, Object>> keys(UUID org) {
    access.require(org, true);
    return db.sql(
            "select id,name,prefix,revoked,created_at from api_key where organization_id=? order by created_at desc")
        .param(org)
        .query(new org.springframework.jdbc.core.ColumnMapRowMapper())
        .list();
  }

  @Transactional
  public Map<String, Object> key(UUID org, String name) {
    access.require(org, true);
    String token = "mf_" + AuthService.randomToken();
    UUID id = UUID.randomUUID();
    db.sql("insert into api_key(id,organization_id,name,token_hash,prefix) values (?,?,?,?,?)")
        .params(id, org, name, AuthService.hash(token), token.substring(0, 11))
        .update();
    return Map.of("id", id, "token", token);
  }

  @Transactional
  public void revoke(UUID org, UUID key) {
    access.require(org, true);
    if (db.sql("update api_key set revoked=true where organization_id=? and id=?")
            .params(org, key)
            .update()
        == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "API key not found");
  }
}
