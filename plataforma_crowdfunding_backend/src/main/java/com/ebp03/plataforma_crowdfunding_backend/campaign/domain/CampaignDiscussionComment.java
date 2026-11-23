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
@Table(name = "campaign_discussion_comments")
public class CampaignDiscussionComment {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "parent_comment_id")
    private UUID parentCommentId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String body;

    @Column(name = "is_official_answer", nullable = false)
    private boolean officialAnswer;

    @Column(name = "is_faq", nullable = false)
    private boolean faq;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CampaignDiscussionComment() {
    }

    public CampaignDiscussionComment(Campaign campaign, UUID authorId, UUID parentCommentId, String body) {
        this.id = UUID.randomUUID();
        this.campaign = campaign;
        this.authorId = authorId;
        this.parentCommentId = parentCommentId;
        this.body = body;
        this.officialAnswer = false;
        this.faq = false;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Campaign getCampaign() { return campaign; }
    public UUID getAuthorId() { return authorId; }
    public UUID getParentCommentId() { return parentCommentId; }
    public String getBody() { return body; }
    public boolean isOfficialAnswer() { return officialAnswer; }
    public boolean isFaq() { return faq; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void markOfficialFaq() { officialAnswer = true; faq = true; }
}
