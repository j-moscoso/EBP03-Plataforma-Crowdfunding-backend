package com.ebp03.plataforma_crowdfunding_backend.campaign.repository;

import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.Campaign;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.CampaignStatus;
import java.util.UUID;
import java.time.Instant;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface CampaignRepository extends JpaRepository<Campaign, UUID> {
    List<Campaign> findByStatusAndDeadlineLessThanEqual(CampaignStatus status, Instant deadline);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Campaign c where c.id = :id")
    java.util.Optional<Campaign> findByIdForUpdate(@Param("id") UUID id);
    Page<Campaign> findByCreatorId(UUID creatorId, Pageable pageable);
    Page<Campaign> findByCreatorIdAndStatus(UUID creatorId, CampaignStatus status, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c from Campaign c
            where c.creatorId = :creatorId
              and c.status = :status
              and upper(c.currency) = upper(:currency)
              and c.deadline > :deadline
            order by c.id
            """)
    List<Campaign> findActiveByCreatorAndCurrencyForUpdate(
            @Param("creatorId") UUID creatorId,
            @Param("status") CampaignStatus status,
            @Param("currency") String currency,
            @Param("deadline") Instant deadline);
    Page<Campaign> findByStatus(CampaignStatus status, Pageable pageable);
    Page<Campaign> findByCategoryIgnoreCase(String category, Pageable pageable);
    Page<Campaign> findByStatusAndCategoryIgnoreCase(CampaignStatus status, String category, Pageable pageable);
}
