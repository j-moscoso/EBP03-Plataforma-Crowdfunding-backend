package com.ebp03.plataforma_crowdfunding_backend.auth.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id
    private UUID id;
    @Column(name = "actor_user_id")
    private UUID actorUserId;
    @Column(name = "target_user_id")
    private UUID targetUserId;
    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;
    @Column(nullable = false, length = 20)
    private String outcome;
    @Column(name = "ip_address", length = 64)
    private String ipAddress;
    @Column(name = "user_agent", length = 512)
    private String userAgent;
    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuditLog() { }

    public AuditLog(UUID actorUserId, UUID targetUserId, String eventType, String outcome,
                    String ipAddress, String userAgent, String metadataJson) {
        this.id = UUID.randomUUID();
        this.actorUserId = actorUserId;
        this.targetUserId = targetUserId;
        this.eventType = eventType;
        this.outcome = outcome;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.metadataJson = metadataJson;
    }

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }
}
