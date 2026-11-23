package com.ebp03.plataforma_crowdfunding_backend.subscription.repository;

import com.ebp03.plataforma_crowdfunding_backend.subscription.domain.SubscriptionChargeAttempt;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionChargeAttemptRepository extends JpaRepository<SubscriptionChargeAttempt, UUID> {
    Optional<SubscriptionChargeAttempt> findByIdempotencyKey(String idempotencyKey);
}
