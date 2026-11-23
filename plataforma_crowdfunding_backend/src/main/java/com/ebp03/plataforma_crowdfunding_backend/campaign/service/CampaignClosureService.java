package com.ebp03.plataforma_crowdfunding_backend.campaign.service;

import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.Campaign;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.CampaignStatus;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.Contribution;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.ContributionStatus;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.FundingModel;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.Payment;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.PaymentStatus;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.TransactionReceiptStatus;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.CampaignRepository;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.ContributionRepository;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.PaymentRepository;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.TransactionReceiptRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampaignClosureService {
    private final CampaignRepository campaigns;
    private final ContributionRepository contributions;
    private final PaymentRepository payments;
    private final TransactionReceiptRepository receipts;

    public CampaignClosureService(CampaignRepository campaigns,
                                  ContributionRepository contributions,
                                  PaymentRepository payments,
                                  TransactionReceiptRepository receipts) {
        this.campaigns = campaigns;
        this.contributions = contributions;
        this.payments = payments;
        this.receipts = receipts;
    }

    @Scheduled(fixedDelayString = "${app.campaign.closure-fixed-delay-ms:60000}",
               initialDelayString = "${app.campaign.closure-fixed-delay-ms:60000}")
    public void scheduledClosure() {
        processExpiredCampaigns(Instant.now());
    }

    @Transactional
    public void processExpiredCampaigns(Instant now) {
        if (now == null) now = Instant.now();
        final Instant effectiveNow = now;
        List<Campaign> expired = campaigns.findByStatusAndDeadlineLessThanEqual(CampaignStatus.ACTIVE, effectiveNow);
        for (Campaign candidate : expired) {
            campaigns.findByIdForUpdate(candidate.getId()).ifPresent(campaign -> {
                if (campaign.getStatus() != CampaignStatus.ACTIVE
                        || campaign.getDeadline().isAfter(effectiveNow)
                        || campaign.getCurrency() == null) {
                    return;
                }
                if (contributions.existsWithDifferentCurrency(campaign.getId(), campaign.getCurrency())) {
                    return;
                }
                closeCampaign(campaign, effectiveNow);
            });
        }
    }

    private void closeCampaign(Campaign campaign, Instant now) {
        List<ContributionStatus> eligibleStatuses = campaign.getFundingModel() == FundingModel.FIXED
                ? List.of(ContributionStatus.CONFIRMED, ContributionStatus.HELD)
                : List.of(ContributionStatus.CONFIRMED);

        BigDecimal raised = contributions.sumAmountByCampaignIdStatusesAndCurrency(
                campaign.getId(), eligibleStatuses, campaign.getCurrency());
        if (raised == null) raised = BigDecimal.ZERO;

        if (campaign.getFundingModel() == FundingModel.FIXED) {
            if (raised.compareTo(campaign.getGoalAmount()) >= 0) {
                campaign.setStatus(CampaignStatus.SUCCESSFUL);
                consolidateFixed(campaign, now);
            } else {
                campaign.setStatus(CampaignStatus.FAILED);
                refundFixed(campaign, now);
            }
        } else if (raised.compareTo(BigDecimal.ZERO) > 0) {
            campaign.setStatus(CampaignStatus.SUCCESSFUL);
            consolidateFlexible(campaign, now);
        } else {
            campaign.setStatus(CampaignStatus.FAILED);
        }
        campaigns.save(campaign);
    }

    private void consolidateFixed(Campaign campaign, Instant now) {
        for (Contribution contribution : contributions.findByCampaignIdAndStatus(
                campaign.getId(), ContributionStatus.HELD)) {
            contribution.setStatus(ContributionStatus.CONFIRMED);
            contribution.setConfirmedAt(contribution.getConfirmedAt() == null ? now : contribution.getConfirmedAt());
            contribution.setClosedAt(now);
            contributions.save(contribution);
            receipts.findByContributionId(contribution.getId()).ifPresent(receipt -> {
                if (receipt.getStatus() == com.ebp03.plataforma_crowdfunding_backend.campaign.domain.TransactionReceiptStatus.HELD) {
                    receipt.setStatus(TransactionReceiptStatus.SUCCESSFUL);
                    receipts.save(receipt);
                }
            });
        }
    }

    private void refundFixed(Campaign campaign, Instant now) {
        for (Contribution contribution : contributions.findByCampaignIdAndStatus(
                campaign.getId(), ContributionStatus.HELD)) {
            contribution.setStatus(ContributionStatus.REFUNDED);
            contribution.setClosedAt(now);
            contributions.save(contribution);

            payments.findFirstByContributionIdOrderByCreatedAtDescIdDesc(contribution.getId())
                    .ifPresent(payment -> {
                        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
                            payment.setStatus(PaymentStatus.REFUNDED);
                            payment.setRefundedAt(now);
                            payments.save(payment);
                        }
                    });
            receipts.findByContributionId(contribution.getId()).ifPresent(receipt -> {
                if (receipt.getStatus() == com.ebp03.plataforma_crowdfunding_backend.campaign.domain.TransactionReceiptStatus.HELD) {
                    receipt.setStatus(TransactionReceiptStatus.REFUNDED);
                    receipts.save(receipt);
                }
            });
        }
    }

    private void consolidateFlexible(Campaign campaign, Instant now) {
        for (Contribution contribution : contributions.findByCampaignIdAndStatus(
                campaign.getId(), ContributionStatus.CONFIRMED)) {
            if (contribution.getClosedAt() == null) {
                contribution.setClosedAt(now);
                contributions.save(contribution);
            }
            receipts.findByContributionId(contribution.getId()).ifPresent(receipt -> {
                if (receipt.getStatus() != TransactionReceiptStatus.REFUNDED
                        && receipt.getStatus() != TransactionReceiptStatus.FAILED) {
                    receipt.setStatus(TransactionReceiptStatus.SUCCESSFUL);
                    receipts.save(receipt);
                }
            });
        }
    }
}
