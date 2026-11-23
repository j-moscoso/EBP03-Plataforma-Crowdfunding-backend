package com.ebp03.plataforma_crowdfunding_backend.campaign.repository;

import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.CampaignReward;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampaignRewardRepository extends JpaRepository<CampaignReward, UUID> {
    @Query("""
            select case
                when reward.quantity is null or reward.claimedQuantity < reward.quantity then true
                else false
            end
            from CampaignReward reward
            where reward.id = :rewardId
            """)
    Optional<Boolean> findAvailabilityById(@Param("rewardId") UUID rewardId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select reward from CampaignReward reward where reward.id = :rewardId and reward.campaign.id = :campaignId")
    Optional<CampaignReward> findByIdAndCampaignIdForUpdate(
            @Param("rewardId") UUID rewardId,
            @Param("campaignId") UUID campaignId);
}
