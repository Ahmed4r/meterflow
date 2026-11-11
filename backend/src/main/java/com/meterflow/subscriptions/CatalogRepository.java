package com.meterflow.subscriptions;

import com.meterflow.billing.Pricing;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogRepository {
  private final JdbcClient db;

  public CatalogRepository(JdbcClient db) {
    this.db = db;
  }

  public record Plan(
      UUID id, String name, String currency, BigDecimal basePrice, List<Pricing.Tier> tiers) {}

  public List<Pricing.Tier> tiers(UUID plan) {
    return db.sql("select up_to,unit_price from pricing_tier where plan_id=? order by position")
        .param(plan)
        .query(Pricing.Tier.class)
        .list();
  }

  public Plan plan(UUID org, UUID id) {
    var p =
        db.sql("select id,name,currency,base_price from plan where organization_id=? and id=?")
            .params(org, id)
            .query(new org.springframework.jdbc.core.ColumnMapRowMapper())
            .optional()
            .orElseThrow(
                () ->
                    new org.springframework.web.server.ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Plan not found"));
    return new Plan(
        id,
        (String) p.get("name"),
        (String) p.get("currency"),
        (BigDecimal) p.get("base_price"),
        tiers(id));
  }

  public List<Plan> plans(UUID org) {
    return db
        .sql("select id from plan where organization_id=? order by created_at desc limit 100")
        .param(org)
        .query(UUID.class)
        .list()
        .stream()
        .map(id -> plan(org, id))
        .toList();
  }

  public UUID createPlan(UUID org, String name, BigDecimal base, List<Pricing.Tier> tiers) {
    UUID id = UUID.randomUUID();
    db.sql("insert into plan(id,organization_id,name,currency,base_price) values (?,?,?,'EUR',?)")
        .params(id, org, name, base)
        .update();
    for (int i = 0; i < tiers.size(); i++) {
      var t = tiers.get(i);
      db.sql("insert into pricing_tier values (:id,:plan,:position,:upto,:price)")
          .param("id", UUID.randomUUID())
          .param("plan", id)
          .param("position", i)
          .param("upto", t.upTo(), java.sql.Types.BIGINT)
          .param("price", t.unitPrice())
          .update();
    }
    return id;
  }

  public List<Map<String, Object>> customers(UUID org, int offset, String search) {
    return db.sql(
            "select id,name,reference,created_at from customer where organization_id=? and (name ilike ? or reference ilike ?) order by created_at desc,id limit 50 offset ?")
        .params(org, "%" + search + "%", "%" + search + "%", offset)
        .query(new org.springframework.jdbc.core.ColumnMapRowMapper())
        .list();
  }

  public UUID createCustomer(UUID org, String name, String reference) {
    UUID id = UUID.randomUUID();
    db.sql("insert into customer(id,organization_id,name,reference) values (?,?,?,?)")
        .params(id, org, name, reference)
        .update();
    return id;
  }

  public List<Map<String, Object>> subscriptions(UUID org, int offset) {
    return db.sql(
            "select s.id,s.customer_id,s.plan_id,c.name customer,p.name plan,s.status,s.starts_at,s.cancelled_at from subscription s join customer c on c.id=s.customer_id join plan p on p.id=s.plan_id where s.organization_id=? order by s.starts_at desc,s.id limit 50 offset ?")
        .params(org, offset)
        .query(new org.springframework.jdbc.core.ColumnMapRowMapper())
        .list();
  }
}
