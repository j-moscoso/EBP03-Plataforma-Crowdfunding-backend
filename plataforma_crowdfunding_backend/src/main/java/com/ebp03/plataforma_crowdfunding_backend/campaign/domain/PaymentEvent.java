package com.ebp03.plataforma_crowdfunding_backend.campaign.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_events")
public class PaymentEvent {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "payment_id") private Payment payment;
    @Column(name = "provider_event_id", nullable = false, unique = true, length = 160) private String providerEventId;
    @Column(name = "event_type", nullable = false, length = 80) private String eventType;
    @Column(name = "payload_hash", nullable = false, length = 128) private String payloadHash;
    @Column(name = "processed_at") private Instant processedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected PaymentEvent() { }
    public PaymentEvent(Payment payment, String providerEventId, String eventType, String payloadHash) {
        this.id = UUID.randomUUID(); this.payment = payment; this.providerEventId = providerEventId;
        this.eventType = eventType; this.payloadHash = payloadHash;
    }
    @PrePersist void onCreate() { createdAt = Instant.now(); }
    public UUID getId() { return id; }
    public String getProviderEventId() { return providerEventId; }
    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
}