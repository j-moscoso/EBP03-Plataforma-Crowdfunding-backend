package com.ebp03.plataforma_crowdfunding_backend.campaign.repository;

import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.Campaign;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.CampaignStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface CampaignRepository extends JpaRepository<Campaign, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Campaign c where c.id = :id")
    java.util.Optional<Campaign> findByIdForUpdate(@Param("id") UUID id);
    Page<Campaign> findByCreatorId(UUID creatorId, Pageable pageable);
    Page<Campaign> findByCreatorIdAndStatus(UUID creatorId, CampaignStatus status, Pageable pageable);
    Page<Campaign> findByStatus(CampaignStatus status, Pageable pageable);
    Page<Campaign> findByCategoryIgnoreCase(String category, Pageable pageable);
    Page<Campaign> findByStatusAndCategoryIgnoreCase(CampaignStatus status, String category, Pageable pageable);
}
