package com.ebp03.plataforma_crowdfunding_backend.campaign.repository;

import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.SubscriptionCampaignAllocation;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionCampaignAllocationRepository
        extends JpaRepository<SubscriptionCampaignAllocation, UUID> {
    @Query("""
            select coalesce(sum(a.amount), 0)
            from SubscriptionCampaignAllocation a
            where a.campaignId = :campaignId and upper(a.currency) = upper(:currency)
            """)
    BigDecimal sumAmountByCampaignIdAndCurrency(
            @Param("campaignId") UUID campaignId,
            @Param("currency") String currency);

    @Query("""
            select distinct a.sponsorId
            from SubscriptionCampaignAllocation a
            where a.campaignId = :campaignId
            """)
    List<UUID> findDistinctSponsorIdsByCampaignId(@Param("campaignId") UUID campaignId);
}
