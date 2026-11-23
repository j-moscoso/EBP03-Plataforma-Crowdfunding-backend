package com.ebp03.plataforma_crowdfunding_backend.auth.api;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String type,
        UUID campaignId,
        UUID campaignUpdateId,
        UUID contributionId,
        String title,
        String body,
        Instant readAt,
        Instant createdAt) {
}
