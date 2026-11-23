package com.ebp03.plataforma_crowdfunding_backend.campaign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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
    @Autowired private JdbcTemplate jdbcTemplate;

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
        String referenceCode = receipts.findByContributionId(contribution.getId()).orElseThrow().getReferenceCode();

        Contribution pending = contributions.save(new Contribution(
                campaign, sponsor.getId(), new BigDecimal("5.00"), "USD", ContributionStatus.PENDING));
        Payment pendingPayment = new Payment(pending, "simulated", pending.getAmount(), BigDecimal.ZERO);
        pendingPayment.setStatus(PaymentStatus.PROCESSING);
        payments.save(pendingPayment);

        Contribution failed = contributions.save(new Contribution(
                campaign, sponsor.getId(), new BigDecimal("5.00"), "USD", ContributionStatus.FAILED));
        Payment failedPayment = new Payment(failed, "simulated", failed.getAmount(), BigDecimal.ZERO);
        failedPayment.setStatus(PaymentStatus.FAILED);
        payments.save(failedPayment);

        Contribution confirmed = contributions.save(new Contribution(
                campaign, sponsor.getId(), new BigDecimal("10.00"), "USD", ContributionStatus.CONFIRMED));
        Payment confirmedPayment = new Payment(confirmed, "simulated", confirmed.getAmount(), BigDecimal.ZERO);
        confirmedPayment.setStatus(PaymentStatus.SUCCEEDED);
        payments.save(confirmedPayment);
        receipts.save(new TransactionReceipt(
                confirmed, "REF-" + UUID.randomUUID(), TransactionReceiptStatus.SUCCESSFUL));

        closureService.processExpiredCampaigns(Instant.now());

        assertEquals(CampaignStatus.FAILED, campaigns.findById(campaign.getId()).orElseThrow().getStatus());
        Contribution refunded = contributions.findById(contribution.getId()).orElseThrow();
        Payment refundedPayment = payments.findFirstByContributionIdOrderByCreatedAtDescIdDesc(contribution.getId())
                .orElseThrow();
        var refundedReceipt = receipts.findByContributionId(contribution.getId()).orElseThrow();
        assertEquals(ContributionStatus.REFUNDED, refunded.getStatus());
        assertNotNull(refunded.getClosedAt());
        assertEquals(PaymentStatus.REFUNDED, refundedPayment.getStatus());
        assertNotNull(refundedPayment.getRefundedAt());
        assertEquals(TransactionReceiptStatus.REFUNDED, refundedReceipt.getStatus());
        assertNotNull(refundedReceipt.getRefundedAt());
        assertEquals(referenceCode, refundedReceipt.getReferenceCode());
        assertEquals(ContributionStatus.PENDING, contributions.findById(pending.getId()).orElseThrow().getStatus());
        assertEquals(PaymentStatus.PROCESSING,
                payments.findFirstByContributionIdOrderByCreatedAtDescIdDesc(pending.getId()).orElseThrow().getStatus());
        assertEquals(ContributionStatus.FAILED, contributions.findById(failed.getId()).orElseThrow().getStatus());
        assertEquals(PaymentStatus.FAILED,
                payments.findFirstByContributionIdOrderByCreatedAtDescIdDesc(failed.getId()).orElseThrow().getStatus());
        assertEquals(ContributionStatus.CONFIRMED, contributions.findById(confirmed.getId()).orElseThrow().getStatus());
        assertEquals(PaymentStatus.SUCCEEDED,
                payments.findFirstByContributionIdOrderByCreatedAtDescIdDesc(confirmed.getId()).orElseThrow().getStatus());
        assertEquals(TransactionReceiptStatus.SUCCESSFUL,
                receipts.findByContributionId(confirmed.getId()).orElseThrow().getStatus());
        assertEquals(1, countRefundNotifications(contribution.getId(), sponsor.getId()));

        closureService.processExpiredCampaigns(Instant.now());
        assertEquals(1, countRefundNotifications(contribution.getId(), sponsor.getId()));
    }

    @Test
    void fixedCampaignAtOrAboveGoalConsolidatesHeldContributionAndReceipt() {
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

        Campaign overGoal = campaigns.save(new Campaign(
                creator.getId(), "Campana fija sobre meta", "Descripcion", new BigDecimal("100.00"),
                Instant.now().minusSeconds(60), "Arte", null, "USD",
                CampaignMediaType.IMAGE, FundingModel.FIXED, CampaignStatus.ACTIVE));
        Contribution overGoalContribution = contributions.save(new Contribution(
                overGoal, sponsor.getId(), new BigDecimal("120.00"), "USD", ContributionStatus.HELD));
        Payment overGoalPayment = new Payment(
                overGoalContribution, "simulated", overGoalContribution.getAmount(), BigDecimal.ZERO);
        overGoalPayment.setStatus(PaymentStatus.SUCCEEDED);
        payments.save(overGoalPayment);
        receipts.save(new TransactionReceipt(
                overGoalContribution, "REF-" + UUID.randomUUID(), TransactionReceiptStatus.HELD));

        closureService.processExpiredCampaigns(Instant.now());
        closureService.processExpiredCampaigns(Instant.now());

        assertEquals(CampaignStatus.SUCCESSFUL, campaigns.findById(campaign.getId()).orElseThrow().getStatus());
        assertEquals(ContributionStatus.CONFIRMED,
                contributions.findById(contribution.getId()).orElseThrow().getStatus());
        assertEquals(TransactionReceiptStatus.SUCCESSFUL,
                receipts.findByContributionId(contribution.getId()).orElseThrow().getStatus());
        assertEquals(CampaignStatus.SUCCESSFUL,
                campaigns.findById(overGoal.getId()).orElseThrow().getStatus());
        assertEquals(ContributionStatus.CONFIRMED,
                contributions.findById(overGoalContribution.getId()).orElseThrow().getStatus());
        assertEquals(PaymentStatus.SUCCEEDED,
                payments.findFirstByContributionIdOrderByCreatedAtDescIdDesc(overGoalContribution.getId())
                        .orElseThrow().getStatus());
        assertEquals(TransactionReceiptStatus.SUCCESSFUL,
                receipts.findByContributionId(overGoalContribution.getId()).orElseThrow().getStatus());
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
        Payment flexiblePayment = new Payment(
                contribution, "simulated", contribution.getAmount(), BigDecimal.ZERO);
        flexiblePayment.setStatus(PaymentStatus.SUCCEEDED);
        payments.save(flexiblePayment);
        receipts.save(new TransactionReceipt(
                contribution, "REF-" + UUID.randomUUID(), TransactionReceiptStatus.SUCCESSFUL));

        Campaign zero = campaigns.save(new Campaign(
                creator.getId(), "Flexible sin aportes", "Descripcion", new BigDecimal("1000.00"),
                Instant.now().minusSeconds(60), "Arte", null, "USD",
                CampaignMediaType.IMAGE, FundingModel.FLEXIBLE, CampaignStatus.ACTIVE));

        Campaign future = campaigns.save(new Campaign(
                creator.getId(), "Campana futura", "Descripcion", new BigDecimal("100.00"),
                Instant.now().plusSeconds(3600), "Arte", null, "USD",
                CampaignMediaType.IMAGE, FundingModel.FIXED, CampaignStatus.ACTIVE));

        closureService.processExpiredCampaigns(Instant.now());

        assertEquals(CampaignStatus.SUCCESSFUL, campaigns.findById(success.getId()).orElseThrow().getStatus());
        assertEquals(CampaignStatus.FAILED, campaigns.findById(zero.getId()).orElseThrow().getStatus());
        assertEquals(ContributionStatus.CONFIRMED, contributions.findById(contribution.getId()).orElseThrow().getStatus());
        assertEquals(PaymentStatus.SUCCEEDED,
                payments.findFirstByContributionIdOrderByCreatedAtDescIdDesc(contribution.getId()).orElseThrow().getStatus());
        assertEquals(TransactionReceiptStatus.SUCCESSFUL,
                receipts.findByContributionId(contribution.getId()).orElseThrow().getStatus());
        assertEquals(CampaignStatus.ACTIVE, campaigns.findById(future.getId()).orElseThrow().getStatus());
    }

    @Test
    void simultaneousClosuresRefundOnlyOnceAndCreateOneNotification() throws Exception {
        User creator = users.save(new User("Concurrent Creator", "concurrent-closure-creator-"
                + UUID.randomUUID() + "@test.local", "hashed", UserRole.CREATOR,
                VerificationStatus.VERIFIED, AccountStatus.ACTIVE));
        User sponsor = users.save(new User("Concurrent Sponsor", "concurrent-closure-sponsor-"
                + UUID.randomUUID() + "@test.local", "hashed", UserRole.SPONSOR,
                VerificationStatus.VERIFIED, AccountStatus.ACTIVE));
        Campaign campaign = campaigns.save(new Campaign(
                creator.getId(), "Cierre concurrente", "Descripcion", new BigDecimal("100.00"),
                Instant.now().minusSeconds(60), "Arte", null, "USD",
                CampaignMediaType.IMAGE, FundingModel.FIXED, CampaignStatus.ACTIVE));
        Contribution contribution = contributions.save(new Contribution(
                campaign, sponsor.getId(), new BigDecimal("20.00"), "USD", ContributionStatus.HELD));
        Payment payment = new Payment(contribution, "simulated", contribution.getAmount(), BigDecimal.ZERO);
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payments.save(payment);
        receipts.save(new TransactionReceipt(
                contribution, "REF-" + UUID.randomUUID(), TransactionReceiptStatus.HELD));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                closeWhenReleased(ready, start);
                return null;
            });
            var second = executor.submit(() -> {
                closeWhenReleased(ready, start);
                return null;
            });
            ready.await();
            start.countDown();
            first.get();
            second.get();
        } finally {
            executor.shutdownNow();
        }

        assertEquals(CampaignStatus.FAILED, campaigns.findById(campaign.getId()).orElseThrow().getStatus());
        assertEquals(ContributionStatus.REFUNDED,
                contributions.findById(contribution.getId()).orElseThrow().getStatus());
        assertEquals(1, countRefundNotifications(contribution.getId(), sponsor.getId()));
    }

    private void closeWhenReleased(CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        closureService.processExpiredCampaigns(Instant.now());
    }

    private int countRefundNotifications(UUID contributionId, UUID sponsorId) {
        return jdbcTemplate.queryForObject("""
                select count(*) from notifications
                where recipient_user_id = ? and contribution_id = ? and type = 'CAMPAIGN_REFUND'
                """, Integer.class, sponsorId, contributionId);
    }

}
