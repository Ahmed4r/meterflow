package com.meterflow.subscriptions;

import com.meterflow.billing.Pricing;
import com.meterflow.identity.TenantAccess;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CatalogService {
  private final TenantAccess access;
  private final CatalogRepository repo;
  private final SubscriptionRepository subscriptions;
  private final JdbcClient db;

  public CatalogService(
      TenantAccess access,
      CatalogRepository repo,
      SubscriptionRepository subscriptions,
      JdbcClient db) {
    this.access = access;
    this.repo = repo;
    this.subscriptions = subscriptions;
    this.db = db;
  }

  public List<CatalogRepository.Plan> plans(UUID org) {
    access.require(org, false);
    return repo.plans(org);
  }

  @Transactional
  public UUID plan(UUID org, String name, BigDecimal base, List<Pricing.Tier> tiers) {
    access.require(org, true);
    Pricing.validate(tiers);
    return repo.createPlan(org, name, base, tiers);
  }

  public List<Map<String, Object>> customers(UUID org, int page, String search) {
    access.require(org, false);
    return repo.customers(org, page * 50, search);
  }

  @Transactional
  public UUID customer(UUID org, String name, String reference) {
    access.require(org, true);
    return repo.createCustomer(org, name, reference);
  }

  public List<Map<String, Object>> subscriptions(UUID org, int page) {
    access.require(org, false);
    return repo.subscriptions(org, page * 50);
  }

  @Transactional
  public UUID subscribe(UUID org, UUID customer, UUID plan) {
    access.require(org, true);
    repo.plan(org, plan);
    if (!db.sql("select exists(select 1 from customer where organization_id=? and id=?)")
        .params(org, customer)
        .query(Boolean.class)
        .single()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found");
    UUID id = UUID.randomUUID();
    db.sql(
            "insert into subscription(id,organization_id,customer_id,plan_id,status,starts_at) values (?,?,?,?,'ACTIVE',?)")
        .params(id, org, customer, plan, OffsetDateTime.now(ZoneOffset.UTC))
        .update();
    history(id, "CREATED");
    return id;
  }

  @Transactional
  public void cancel(UUID org, UUID id) {
    access.require(org, true);
    var sub =
        subscriptions
            .lock(org, id)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found"));
    if (sub.status().equals("CANCELLED")) return;
    db.sql(
            "update subscription set status='CANCELLED',cancelled_at=now(),version=version+1 where id=?")
        .param(id)
        .update();
    history(id, "CANCELLED");
  }

  public List<Map<String, Object>> history(UUID org, UUID id) {
    access.require(org, false);
    return db.sql(
            "select h.action,h.created_at from subscription_history h join subscription s on s.id=h.subscription_id where s.organization_id=? and s.id=? order by h.created_at")
        .params(org, id)
        .query(new org.springframework.jdbc.core.ColumnMapRowMapper())
        .list();
  }

  private void history(UUID sub, String action) {
    db.sql("insert into subscription_history(id,subscription_id,action) values (?,?,?)")
        .params(UUID.randomUUID(), sub, action)
        .update();
  }
}
