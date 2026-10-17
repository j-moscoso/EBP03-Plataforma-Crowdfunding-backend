package com.ebp03.plataforma_crowdfunding_backend.campaign.api;

import java.util.UUID;

public record CampaignUpdateMediaResponse(UUID id, String mediaType, String url, int sortOrder) {
}
