package com.webizon.tenancy.repo;

import com.webizon.tenancy.model.Subscription;
import com.webizon.tenancy.model.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    /**
     * Find the single non-terminal subscription for the currently-resolved tenant.
     * There is a partial unique index in V001 that guarantees at most one row
     * in {@code (TRIAL, ACTIVE, PAST_DUE)} per tenant.
     */
    Optional<Subscription> findFirstByStatusIn(List<SubscriptionStatus> statuses);

    default Optional<Subscription> findActive() {
        return findFirstByStatusIn(List.of(
                SubscriptionStatus.TRIAL,
                SubscriptionStatus.ACTIVE,
                SubscriptionStatus.PAST_DUE
        ));
    }
}
