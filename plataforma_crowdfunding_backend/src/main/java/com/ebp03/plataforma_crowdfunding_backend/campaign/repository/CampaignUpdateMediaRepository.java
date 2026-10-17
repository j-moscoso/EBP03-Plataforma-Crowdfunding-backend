package com.ebp03.plataforma_crowdfunding_backend.campaign.repository;

import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.CampaignUpdateMedia;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignUpdateMediaRepository extends JpaRepository<CampaignUpdateMedia, UUID> {
    List<CampaignUpdateMedia> findByUpdate_IdInOrderBySortOrderAscIdAsc(List<UUID> updateIds);
}
