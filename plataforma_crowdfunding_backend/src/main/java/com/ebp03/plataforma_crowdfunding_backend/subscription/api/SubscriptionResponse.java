package com.ebp03.plataforma_crowdfunding_backend.subscription.api;

import com.ebp03.plataforma_crowdfunding_backend.subscription.domain.CreatorSubscription;
import com.ebp03.plataforma_crowdfunding_backend.subscription.domain.CreatorSubscriptionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SubscriptionResponse(
        UUID id,
        UUID creatorId,
        BigDecimal amount,
        String currency,
        CreatorSubscriptionStatus status,
        Instant nextChargeAt,
        Instant lastChargeAt,
        Instant cancelledAt,
        Instant createdAt) {

    public static SubscriptionResponse from(CreatorSubscription subscription) {
        return new SubscriptionResponse(
                subscription.getId(),
                subscription.getCreatorId(),
                subscription.getAmount(),
                subscription.getCurrency(),
                subscription.getStatus(),
                subscription.getNextChargeAt(),
                subscription.getLastChargeAt(),
                subscription.getCancelledAt(),
                subscription.getCreatedAt());
    }
}
