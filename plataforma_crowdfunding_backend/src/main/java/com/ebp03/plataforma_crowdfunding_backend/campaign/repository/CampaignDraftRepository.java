package com.ebp03.plataforma_crowdfunding_backend.campaign.repository;

import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.CampaignDraft;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignDraftRepository extends JpaRepository<CampaignDraft, UUID> {
    Page<CampaignDraft> findByCreatorId(UUID creatorId, Pageable pageable);
    Optional<CampaignDraft> findByIdAndCreatorId(UUID id, UUID creatorId);
}
