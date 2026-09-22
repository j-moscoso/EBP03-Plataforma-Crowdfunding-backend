package com.ebp03.plataforma_crowdfunding_backend.campaign.repository;

import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.Campaign;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.CampaignStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignRepository extends JpaRepository<Campaign, UUID> {
    Page<Campaign> findByCreatorId(UUID creatorId, Pageable pageable);
    Page<Campaign> findByCreatorIdAndStatus(UUID creatorId, CampaignStatus status, Pageable pageable);
    Page<Campaign> findByStatus(CampaignStatus status, Pageable pageable);
    Page<Campaign> findByCategoryIgnoreCase(String category, Pageable pageable);
    Page<Campaign> findByStatusAndCategoryIgnoreCase(CampaignStatus status, String category, Pageable pageable);
}
