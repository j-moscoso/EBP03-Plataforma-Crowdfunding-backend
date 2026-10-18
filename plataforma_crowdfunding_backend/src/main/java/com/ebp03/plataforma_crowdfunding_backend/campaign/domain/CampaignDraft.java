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
@Table(name = "campaign_drafts")
public class CampaignDraft {
    @Id
    private UUID id;

    @Column(name = "creator_id", nullable = false)
    private UUID creatorId;

    @Column(length = 160)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "goal_amount", precision = 19, scale = 2)
    private BigDecimal goalAmount;

    @Column(name = "duration_days")
    private Integer durationDays;

    @Column(length = 80)
    private String category;

    @Column(name = "media_url", length = 500)
    private String mediaUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 20)
    private CampaignMediaType mediaType = CampaignMediaType.IMAGE;

    @Column(length = 3)
    private String currency;

    @Column(name = "completion_percentage", nullable = false)
    private Integer completionPercentage;

    @Column(name = "saved_at", nullable = false)
    private Instant savedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "draft", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<DraftReward> rewards = new ArrayList<>();

    protected CampaignDraft() {
    }

    public CampaignDraft(UUID creatorId, String title, String description, BigDecimal goalAmount,
                        Integer durationDays, String category, String mediaUrl) {
        this(creatorId, title, description, goalAmount, durationDays, category, mediaUrl, null);
    }

    public CampaignDraft(UUID creatorId, String title, String description, BigDecimal goalAmount,
                        Integer durationDays, String category, String mediaUrl, String currency) {
        this(creatorId, title, description, goalAmount, durationDays, category, mediaUrl, currency,
                CampaignMediaType.IMAGE);
    }

    public CampaignDraft(UUID creatorId, String title, String description, BigDecimal goalAmount,
                        Integer durationDays, String category, String mediaUrl, String currency,
                        CampaignMediaType mediaType) {
        this.id = UUID.randomUUID();
        this.creatorId = creatorId;
        this.title = title;
        this.description = description;
        this.goalAmount = goalAmount;
        this.durationDays = durationDays;
        this.category = category;
        this.mediaUrl = mediaUrl;
        this.mediaType = mediaType == null ? CampaignMediaType.IMAGE : mediaType;
        this.currency = currency;
        this.completionPercentage = 0;
        this.savedAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.savedAt == null) {
            this.savedAt = now;
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
    public Integer getDurationDays() { return durationDays; }
    public String getCategory() { return category; }
    public String getMediaUrl() { return mediaUrl; }
    public CampaignMediaType getMediaType() { return mediaType; }
    public String getCurrency() { return currency; }
    public Integer getCompletionPercentage() { return completionPercentage; }
    public Instant getSavedAt() { return savedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<DraftReward> getRewards() { return rewards; }

    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setGoalAmount(BigDecimal goalAmount) { this.goalAmount = goalAmount; }
    public void setDurationDays(Integer durationDays) { this.durationDays = durationDays; }
    public void setCategory(String category) { this.category = category; }
    public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }
    public void setMediaType(CampaignMediaType mediaType) { this.mediaType = mediaType; }
    public void setCurrency(String currency) { this.currency = currency; }
    public void setCompletionPercentage(Integer completionPercentage) { this.completionPercentage = completionPercentage; }
    public void setSavedAt(Instant savedAt) { this.savedAt = savedAt; }
    public void setRewards(List<DraftReward> rewards) { this.rewards.clear(); if (rewards != null) { this.rewards.addAll(rewards); for (DraftReward reward : this.rewards) { reward.setDraft(this); } } }
}
