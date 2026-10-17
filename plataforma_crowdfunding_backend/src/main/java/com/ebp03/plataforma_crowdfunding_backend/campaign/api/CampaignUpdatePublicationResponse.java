package com.ebp03.plataforma_crowdfunding_backend.campaign.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CampaignUpdatePublicationResponse(
        UUID id,
        UUID campaignId,
        UUID authorId,
        String title,
        String body,
        Instant publishedAt,
        List<CampaignUpdateMediaResponse> media,
        int notificationsCreated) {
}
