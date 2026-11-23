package com.ebp03.plataforma_crowdfunding_backend.campaign;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ebp03.plataforma_crowdfunding_backend.auth.domain.AccountStatus;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.User;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.UserRole;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.VerificationStatus;
import com.ebp03.plataforma_crowdfunding_backend.auth.repository.UserRepository;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.Campaign;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.CampaignMediaType;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.CampaignStatus;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.Contribution;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.ContributionStatus;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.FundingModel;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.Payment;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.PaymentStatus;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.TransactionReceipt;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.TransactionReceiptStatus;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.CampaignRepository;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.ContributionRepository;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.PaymentRepository;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.TransactionReceiptRepository;
import com.ebp03.plataforma_crowdfunding_backend.campaign.service.CampaignClosureService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class CampaignClosureIntegrationTests {
    @Autowired private UserRepository users;
    @Autowired private CampaignRepository campaigns;
    @Autowired private ContributionRepository contributions;
    @Autowired private PaymentRepository payments;
    @Autowired private TransactionReceiptRepository receipts;
    @Autowired private CampaignClosureService closureService;

    @Test
    void fixedCampaignBelowGoalRefundsHeldContributionAndReceipt() {
        User creator = users.save(new User("Fixed Creator", "fixed-refund-" + UUID.randomUUID() + "@test.local",
                "hashed", UserRole.CREATOR, VerificationStatus.VERIFIED, AccountStatus.ACTIVE));
        User sponsor = users.save(new User("Fixed Sponsor", "fixed-sponsor-" + UUID.randomUUID() + "@test.local",
                "hashed", UserRole.SPONSOR, VerificationStatus.VERIFIED, AccountStatus.ACTIVE));
        Campaign campaign = campaigns.save(new Campaign(
                creator.getId(), "Campana fija fallida", "Descripcion", new BigDecimal("100.00"),
                Instant.now().minusSeconds(60), "Arte", null, "USD",
                CampaignMediaType.IMAGE, FundingModel.FIXED, CampaignStatus.ACTIVE));

        Contribution contribution = contributions.save(new Contribution(
                campaign, sponsor.getId(), new BigDecimal("40.00"), "USD",
                ContributionStatus.HELD));
        Payment payment = new Payment(contribution, "simulated", contribution.getAmount(), BigDecimal.ZERO);
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payments.save(payment);
        receipts.save(new TransactionReceipt(
                contribution, "REF-" + UUID.randomUUID(), TransactionReceiptStatus.HELD));

        closureService.processExpiredCampaigns(Instant.now());

        assertEquals(CampaignStatus.FAILED, campaigns.findById(campaign.getId()).orElseThrow().getStatus());
        assertEquals(ContributionStatus.REFUNDED,
                contributions.findById(contribution.getId()).orElseThrow().getStatus());
        assertEquals(PaymentStatus.REFUNDED,
                payments.findFirstByContributionIdOrderByCreatedAtDescIdDesc(contribution.getId())
                        .orElseThrow().getStatus());
        assertEquals(TransactionReceiptStatus.REFUNDED,
                receipts.findByContributionId(contribution.getId()).orElseThrow().getStatus());
    }

    @Test
    void fixedCampaignAtGoalConsolidatesHeldContributionAndReceipt() {
        User creator = users.save(new User("Fixed Success", "fixed-success-" + UUID.randomUUID() + "@test.local",
                "hashed", UserRole.CREATOR, VerificationStatus.VERIFIED, AccountStatus.ACTIVE));
        User sponsor = users.save(new User("Fixed Sponsor", "fixed-success-sponsor-" + UUID.randomUUID() + "@test.local",
                "hashed", UserRole.SPONSOR, VerificationStatus.VERIFIED, AccountStatus.ACTIVE));
        Campaign campaign = campaigns.save(new Campaign(
                creator.getId(), "Campana fija exitosa", "Descripcion", new BigDecimal("100.00"),
                Instant.now().minusSeconds(60), "Arte", null, "USD",
                CampaignMediaType.IMAGE, FundingModel.FIXED, CampaignStatus.ACTIVE));
        Contribution contribution = contributions.save(new Contribution(
                campaign, sponsor.getId(), new BigDecimal("100.00"), "USD",
                ContributionStatus.HELD));
        Payment payment = new Payment(contribution, "simulated", contribution.getAmount(), BigDecimal.ZERO);
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payments.save(payment);
        receipts.save(new TransactionReceipt(
                contribution, "REF-" + UUID.randomUUID(), TransactionReceiptStatus.HELD));

        closureService.processExpiredCampaigns(Instant.now());
        closureService.processExpiredCampaigns(Instant.now());

        assertEquals(CampaignStatus.SUCCESSFUL, campaigns.findById(campaign.getId()).orElseThrow().getStatus());
        assertEquals(ContributionStatus.CONFIRMED,
                contributions.findById(contribution.getId()).orElseThrow().getStatus());
        assertEquals(TransactionReceiptStatus.SUCCESSFUL,
                receipts.findByContributionId(contribution.getId()).orElseThrow().getStatus());
    }

    @Test
    void flexibleCampaignWithPositiveRaisedAmountConsolidatesAndZeroCampaignFails() {
        User creator = users.save(new User("Flexible Creator", "flexible-" + UUID.randomUUID() + "@test.local",
                "hashed", UserRole.CREATOR, VerificationStatus.VERIFIED, AccountStatus.ACTIVE));
        User sponsor = users.save(new User("Flexible Sponsor", "flexible-sponsor-" + UUID.randomUUID() + "@test.local",
                "hashed", UserRole.SPONSOR, VerificationStatus.VERIFIED, AccountStatus.ACTIVE));
        Campaign success = campaigns.save(new Campaign(
                creator.getId(), "Flexible exitosa", "Descripcion", new BigDecimal("1000.00"),
                Instant.now().minusSeconds(60), "Arte", null, "USD",
                CampaignMediaType.IMAGE, FundingModel.FLEXIBLE, CampaignStatus.ACTIVE));
        Contribution contribution = contributions.save(new Contribution(
                success, sponsor.getId(), new BigDecimal("10.00"), "USD",
                ContributionStatus.CONFIRMED));
        receipts.save(new TransactionReceipt(
                contribution, "REF-" + UUID.randomUUID(), TransactionReceiptStatus.SUCCESSFUL));

        Campaign zero = campaigns.save(new Campaign(
                creator.getId(), "Flexible sin aportes", "Descripcion", new BigDecimal("1000.00"),
                Instant.now().minusSeconds(60), "Arte", null, "USD",
                CampaignMediaType.IMAGE, FundingModel.FLEXIBLE, CampaignStatus.ACTIVE));

        closureService.processExpiredCampaigns(Instant.now());

        assertEquals(CampaignStatus.SUCCESSFUL, campaigns.findById(success.getId()).orElseThrow().getStatus());
        assertEquals(CampaignStatus.FAILED, campaigns.findById(zero.getId()).orElseThrow().getStatus());
    }
}
