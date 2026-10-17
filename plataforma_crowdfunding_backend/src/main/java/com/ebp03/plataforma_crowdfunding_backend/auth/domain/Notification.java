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
        this.id = UUID.randomUUID();
        this.recipientUserId = recipientUserId;
        this.type = type;
        this.title = title;
        this.body = body;
    }

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }
}
