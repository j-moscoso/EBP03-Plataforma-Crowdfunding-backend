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
@Table(name = "draft_rewards")
public class DraftReward {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "draft_id", nullable = false)
    private CampaignDraft draft;

    @Column(length = 120, nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "minimum_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal minimumAmount;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DraftReward() {
    }

    public DraftReward(CampaignDraft draft, String title, String description, BigDecimal minimumAmount, Integer sortOrder) {
        this.id = UUID.randomUUID();
        this.draft = draft;
        this.title = title;
        this.description = description;
        this.minimumAmount = minimumAmount;
        this.sortOrder = sortOrder;
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
    public CampaignDraft getDraft() { return draft; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public BigDecimal getMinimumAmount() { return minimumAmount; }
    public Integer getSortOrder() { return sortOrder; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setDraft(CampaignDraft draft) { this.draft = draft; }
    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setMinimumAmount(BigDecimal minimumAmount) { this.minimumAmount = minimumAmount; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
