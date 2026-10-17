package com.ebp03.plataforma_crowdfunding_backend.campaign.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CampaignUpdateWallResponse(
        List<Item> content,
        int page,
        int pageSize,
        long totalElements,
        int totalPages) {
    public record Item(
            UUID id,
            UUID campaignId,
            UUID authorId,
            String title,
            String body,
            Instant publishedAt,
            List<CampaignUpdateMediaResponse> media) { }
}
