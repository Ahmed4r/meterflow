package com.meterflow.identity;

import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class IdentityRepository {
  private final JdbcClient db;

  public IdentityRepository(JdbcClient db) {
    this.db = db;
  }

  public record User(UUID id, String email, String passwordHash, String name) {}

  public Optional<User> byEmail(String email) {
    return db.sql("select id,email,password_hash,name from app_user where email=:email")
        .param("email", email)
        .query(User.class)
        .optional();
  }

  public Optional<User> byId(UUID id) {
    return db.sql("select id,email,password_hash,name from app_user where id=:id")
        .param("id", id)
        .query(User.class)
        .optional();
  }

  public void create(UUID id, String email, String hash, String name) {
    db.sql("insert into app_user(id,email,password_hash,name) values (?,?,?,?)")
        .params(id, email, hash, name)
        .update();
  }
}
