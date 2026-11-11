package com.meterflow.subscriptions;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscription")
public class Subscription {
  @Id private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(name = "plan_id", nullable = false)
  private UUID planId;

  private String status;
  private Instant startsAt;
  private Instant cancelledAt;
  @Version private long version;

  protected Subscription() {}

  public UUID id() {
    return id;
  }

  public UUID organizationId() {
    return organizationId;
  }

  public UUID planId() {
    return planId;
  }

  public String status() {
    return status;
  }

  public Instant startsAt() {
    return startsAt;
  }

  public Instant cancelledAt() {
    return cancelledAt;
  }
}
