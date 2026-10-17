package com.ebp03.plataforma_crowdfunding_backend.auth.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {
    @Id
    private UUID id;
    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;
    @Column(name = "campaign_id")
    private UUID campaignId;
    @Column(name = "campaign_update_id")
    private UUID campaignUpdateId;
    @Column(nullable = false, length = 50)
    private String type;
    @Column(nullable = false, length = 160)
    private String title;
    @Column(nullable = false, length = 500)
    private String body;
    @Column(name = "read_at")
    private Instant readAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Notification() { }

    public Notification(UUID recipientUserId, String type, String title, String body) {
        this(recipientUserId, null, null, type, title, body);
    }

    public Notification(UUID recipientUserId, UUID campaignId, UUID campaignUpdateId,
                        String type, String title, String body) {
        this.id = UUID.randomUUID();
        this.recipientUserId = recipientUserId;
        this.campaignId = campaignId;
        this.campaignUpdateId = campaignUpdateId;
        this.type = type;
        this.title = title;
        this.body = body;
    }

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getRecipientUserId() { return recipientUserId; }
    public UUID getCampaignId() { return campaignId; }
    public UUID getCampaignUpdateId() { return campaignUpdateId; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public Instant getReadAt() { return readAt; }
    public Instant getCreatedAt() { return createdAt; }
}
