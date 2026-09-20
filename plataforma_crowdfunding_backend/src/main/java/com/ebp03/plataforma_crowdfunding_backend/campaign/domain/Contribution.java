package com.ebp03.plataforma_crowdfunding_backend.campaign.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "contributions")
public class Contribution {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reward_id")
    private CampaignReward reward;

    @Column(name = "sponsor_id", nullable = false)
    private UUID sponsorId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(length = 3, nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContributionStatus status;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Contribution() {
    }

    public Contribution(Campaign campaign, UUID sponsorId, BigDecimal amount, String currency, ContributionStatus status) {
        this(campaign, sponsorId, null, amount, currency, status, UUID.randomUUID().toString());
    }

    public Contribution(Campaign campaign, UUID sponsorId, CampaignReward reward, BigDecimal amount,
                        String currency, ContributionStatus status, String idempotencyKey) {
        this.id = UUID.randomUUID();
        this.campaign = campaign;
        this.sponsorId = sponsorId;
        this.reward = reward;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Campaign getCampaign() { return campaign; }
    public UUID getSponsorId() { return sponsorId; }
    public CampaignReward getReward() { return reward; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public ContributionStatus getStatus() { return status; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setCampaign(Campaign campaign) { this.campaign = campaign; }
    public void setStatus(ContributionStatus status) { this.status = status; }
    public void setConfirmedAt(Instant confirmedAt) { this.confirmedAt = confirmedAt; }
}
