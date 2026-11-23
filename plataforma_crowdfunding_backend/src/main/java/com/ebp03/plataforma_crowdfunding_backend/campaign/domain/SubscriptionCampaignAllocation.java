package com.ebp03.plataforma_crowdfunding_backend.campaign.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscription_campaign_allocations")
public class SubscriptionCampaignAllocation {
    @Id
    private UUID id;

    @Column(name = "subscription_charge_attempt_id", nullable = false)
    private UUID subscriptionChargeAttemptId;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(name = "sponsor_id", nullable = false)
    private UUID sponsorId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SubscriptionCampaignAllocation() {
    }

    public SubscriptionCampaignAllocation(
            UUID subscriptionChargeAttemptId,
            UUID campaignId,
            UUID sponsorId,
            BigDecimal amount,
            String currency) {
        this.id = UUID.randomUUID();
        this.subscriptionChargeAttemptId = subscriptionChargeAttemptId;
        this.campaignId = campaignId;
        this.sponsorId = sponsorId;
        this.amount = amount;
        this.currency = currency;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getSubscriptionChargeAttemptId() { return subscriptionChargeAttemptId; }
    public UUID getCampaignId() { return campaignId; }
    public UUID getSponsorId() { return sponsorId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public Instant getCreatedAt() { return createdAt; }
}
