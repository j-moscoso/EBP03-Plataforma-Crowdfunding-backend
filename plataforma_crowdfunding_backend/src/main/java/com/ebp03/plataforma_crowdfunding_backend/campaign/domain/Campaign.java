package com.ebp03.plataforma_crowdfunding_backend.campaign.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "campaigns")
public class Campaign {
    @Id
    private UUID id;

    @Column(name = "creator_id", nullable = false)
    private UUID creatorId;

    @Column(length = 160, nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(name = "goal_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal goalAmount;

    @Column(nullable = false)
    private Instant deadline;

    @Column(length = 80, nullable = false)
    private String category;

    @Column(name = "media_url", length = 500)
    private String mediaUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CampaignStatus status;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<CampaignReward> rewards = new ArrayList<>();

    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<CampaignUpdate> updates = new ArrayList<>();

    protected Campaign() {
    }

    public Campaign(UUID creatorId, String title, String description, BigDecimal goalAmount,
                   Instant deadline, String category, String mediaUrl, CampaignStatus status) {
        this.id = UUID.randomUUID();
        this.creatorId = creatorId;
        this.title = title;
        this.description = description;
        this.goalAmount = goalAmount;
        this.deadline = deadline;
        this.category = category;
        this.mediaUrl = mediaUrl;
        this.status = status;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.publishedAt == null && this.status == CampaignStatus.ACTIVE) {
            this.publishedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getCreatorId() { return creatorId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public BigDecimal getGoalAmount() { return goalAmount; }
    public Instant getDeadline() { return deadline; }
    public String getCategory() { return category; }
    public String getMediaUrl() { return mediaUrl; }
    public CampaignStatus getStatus() { return status; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<CampaignReward> getRewards() { return rewards; }
    public List<CampaignUpdate> getUpdates() { return updates; }

    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setGoalAmount(BigDecimal goalAmount) { this.goalAmount = goalAmount; }
    public void setDeadline(Instant deadline) { this.deadline = deadline; }
    public void setCategory(String category) { this.category = category; }
    public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }
    public void setStatus(CampaignStatus status) { this.status = status; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
    public void setRewards(List<CampaignReward> rewards) { this.rewards.clear(); if (rewards != null) { this.rewards.addAll(rewards); for (CampaignReward reward : this.rewards) { reward.setCampaign(this); } } }
    public void addUpdate(CampaignUpdate update) { this.updates.add(update); update.setCampaign(this); }
}
