package com.ebp03.plataforma_crowdfunding_backend.campaign.service;

import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.Campaign;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.CampaignUpdate;
import java.util.List;

public interface NotificationPublisher {
    int publishCampaignUpdate(Campaign campaign, CampaignUpdate update, List<java.util.UUID> recipientIds);
}
