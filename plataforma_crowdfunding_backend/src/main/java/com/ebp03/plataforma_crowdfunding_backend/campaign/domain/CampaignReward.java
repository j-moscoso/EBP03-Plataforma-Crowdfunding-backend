package com.ebp03.plataforma_crowdfunding_backend.campaign.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "campaign_rewards")
public class CampaignReward {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @Column(length = 120, nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "minimum_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal minimumAmount;

    @Column(nullable = true)
    private Integer quantity;

    @Column(name = "claimed_quantity", nullable = false)
    private Integer claimedQuantity;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CampaignReward() {
    }

    public CampaignReward(Campaign campaign, String title, String description,
                         BigDecimal minimumAmount, Integer quantity, Integer claimedQuantity) {
        this.id = UUID.randomUUID();
        this.campaign = campaign;
        this.title = title;
        this.description = description;
        this.minimumAmount = minimumAmount;
        this.quantity = quantity;
        this.claimedQuantity = claimedQuantity == null ? 0 : claimedQuantity;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.claimedQuantity == null) {
            this.claimedQuantity = 0;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Campaign getCampaign() { return campaign; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public BigDecimal getMinimumAmount() { return minimumAmount; }
    public Integer getQuantity() { return quantity; }
    public Integer getClaimedQuantity() { return claimedQuantity; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setCampaign(Campaign campaign) { this.campaign = campaign; }
    public void setClaimedQuantity(Integer claimedQuantity) { this.claimedQuantity = claimedQuantity; }
}
