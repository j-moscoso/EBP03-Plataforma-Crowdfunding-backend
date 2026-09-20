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
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "campaign_updates")
public class CampaignUpdate {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(length = 140, nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String body;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CampaignUpdate() {
    }

    public CampaignUpdate(Campaign campaign, UUID authorId, String title, String body) {
        this.id = UUID.randomUUID();
        this.campaign = campaign;
        this.authorId = authorId;
        this.title = title;
        this.body = body;
        this.publishedAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.publishedAt == null) {
            this.publishedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Campaign getCampaign() { return campaign; }
    public UUID getAuthorId() { return authorId; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setCampaign(Campaign campaign) { this.campaign = campaign; }
}
