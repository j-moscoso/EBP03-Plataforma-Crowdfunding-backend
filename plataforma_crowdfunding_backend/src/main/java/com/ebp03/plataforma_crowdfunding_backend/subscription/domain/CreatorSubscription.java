package com.ebp03.plataforma_crowdfunding_backend.subscription.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "creator_subscriptions")
public class CreatorSubscription {
    @Id
    private UUID id;

    @Column(name = "sponsor_id", nullable = false)
    private UUID sponsorId;

    @Column(name = "creator_id", nullable = false)
    private UUID creatorId;

    @Column(name = "payment_method_ref", nullable = false, length = 160)
    private String paymentMethodRef;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CreatorSubscriptionStatus status;

    @Column(name = "next_charge_at", nullable = false)
    private Instant nextChargeAt;

    @Column(name = "last_charge_at")
    private Instant lastChargeAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CreatorSubscription() {
    }

    public CreatorSubscription(
            UUID sponsorId,
            UUID creatorId,
            String paymentMethodRef,
            BigDecimal amount,
            String currency,
            Instant nextChargeAt) {
        this.id = UUID.randomUUID();
        this.sponsorId = sponsorId;
        this.creatorId = creatorId;
        this.paymentMethodRef = paymentMethodRef;
        this.amount = amount;
        this.currency = currency;
        this.status = CreatorSubscriptionStatus.ACTIVE;
        this.nextChargeAt = nextChargeAt;
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

    public void completeCharge(Instant chargedAt, Instant nextCharge) {
        lastChargeAt = chargedAt;
        nextChargeAt = nextCharge;
    }

    public void scheduleNextCharge(Instant nextCharge) {
        nextChargeAt = nextCharge;
    }

    public void cancel(Instant cancelledAt) {
        if (status == CreatorSubscriptionStatus.CANCELLED) return;
        status = CreatorSubscriptionStatus.CANCELLED;
        this.cancelledAt = cancelledAt;
    }

    public UUID getId() { return id; }
    public UUID getSponsorId() { return sponsorId; }
    public UUID getCreatorId() { return creatorId; }
    public String getPaymentMethodRef() { return paymentMethodRef; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public CreatorSubscriptionStatus getStatus() { return status; }
    public Instant getNextChargeAt() { return nextChargeAt; }
    public Instant getLastChargeAt() { return lastChargeAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public Instant getCreatedAt() { return createdAt; }
}
