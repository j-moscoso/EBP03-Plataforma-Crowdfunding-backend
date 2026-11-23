package com.ebp03.plataforma_crowdfunding_backend.campaign.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CampaignRewardAvailabilityTests {
    @Test
    void zeroAndFullyClaimedHistoricalRewardsAreUnavailableAndUnlimitedRewardsRemainAvailable() {
        CampaignService.RewardResponse zeroQuantity = CampaignService.rewardResponse(
                UUID.randomUUID(), "Cupo legado", "Descripción", BigDecimal.ONE, null, 0, 0);
        CampaignService.RewardResponse fullyClaimed = CampaignService.rewardResponse(
                UUID.randomUUID(), "Agotada", "Descripción", BigDecimal.ONE, null, 2, 2);
        CampaignService.RewardResponse unlimited = CampaignService.rewardResponse(
                UUID.randomUUID(), "Ilimitada", "Descripción", BigDecimal.ONE, null, null, 12);

        assertThat(zeroQuantity.available()).isFalse();
        assertThat(zeroQuantity.remainingQuantity()).isZero();
        assertThat(fullyClaimed.available()).isFalse();
        assertThat(fullyClaimed.remainingQuantity()).isZero();
        assertThat(unlimited.available()).isTrue();
        assertThat(unlimited.remainingQuantity()).isNull();
    }
}
