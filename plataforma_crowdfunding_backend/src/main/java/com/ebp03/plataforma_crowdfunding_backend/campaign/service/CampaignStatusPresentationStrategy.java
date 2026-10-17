package com.ebp03.plataforma_crowdfunding_backend.campaign.service;

import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.CampaignStatus;

public interface CampaignStatusPresentationStrategy {
    Presentation present(CampaignStatus status);

    record Presentation(String status, String statusCode) {
    }
}
