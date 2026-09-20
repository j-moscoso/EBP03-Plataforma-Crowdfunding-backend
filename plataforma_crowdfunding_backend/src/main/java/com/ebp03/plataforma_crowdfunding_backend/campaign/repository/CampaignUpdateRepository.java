package com.ebp03.plataforma_crowdfunding_backend.campaign.repository;

import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.CampaignUpdate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignUpdateRepository extends JpaRepository<CampaignUpdate, UUID> {
    List<CampaignUpdate> findByCampaignIdOrderByPublishedAtDesc(UUID campaignId);
}
