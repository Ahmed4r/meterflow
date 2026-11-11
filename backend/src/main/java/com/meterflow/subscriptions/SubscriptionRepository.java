package com.meterflow.subscriptions;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from Subscription s where s.id=:id and s.organizationId=:org")
  Optional<Subscription> lock(@Param("org") UUID org, @Param("id") UUID id);
}
