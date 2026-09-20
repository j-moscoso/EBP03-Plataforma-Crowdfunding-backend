package com.ebp03.plataforma_crowdfunding_backend.campaign.repository;

import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.Contribution;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.ContributionStatus;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContributionRepository extends JpaRepository<Contribution, UUID> {
    java.util.Optional<Contribution> findBySponsorIdAndIdempotencyKey(UUID sponsorId, String idempotencyKey);
    org.springframework.data.domain.Page<Contribution> findBySponsorId(UUID sponsorId, org.springframework.data.domain.Pageable pageable);
    org.springframework.data.domain.Page<Contribution> findBySponsorIdAndStatus(UUID sponsorId, ContributionStatus status, org.springframework.data.domain.Pageable pageable);
    @Query("select coalesce(sum(c.amount), 0) from Contribution c where c.campaign.id = :campaignId and c.status = :status")
    BigDecimal sumAmountByCampaignIdAndStatus(@Param("campaignId") UUID campaignId, @Param("status") ContributionStatus status);

    @Query("select count(distinct c.sponsorId) from Contribution c where c.campaign.id = :campaignId and c.status = :status")
    Long countDistinctSponsorByCampaignIdAndStatus(@Param("campaignId") UUID campaignId, @Param("status") ContributionStatus status);
}
