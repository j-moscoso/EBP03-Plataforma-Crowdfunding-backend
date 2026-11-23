package com.ebp03.plataforma_crowdfunding_backend.subscription.domain;

import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.Contribution;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.Payment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "subscription_charge_attempts")
public class SubscriptionChargeAttempt {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false)
    private CreatorSubscription subscription;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionChargeAttemptStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contribution_id")
    private Contribution contribution;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Column(name = "failure_code", length = 80)
    private String failureCode;

    @Column(name = "failure_message", length = 500)
    private String failureMessage;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SubscriptionChargeAttempt() {
    }

    public SubscriptionChargeAttempt(
            CreatorSubscription subscription,
            Instant scheduledAt,
            String idempotencyKey) {
        this.id = UUID.randomUUID();
        this.subscription = subscription;
        this.scheduledAt = scheduledAt;
        this.idempotencyKey = idempotencyKey;
        this.status = SubscriptionChargeAttemptStatus.PENDING;
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

    public void markSucceeded(Contribution contribution, Payment payment, Instant processedAt) {
        this.contribution = contribution;
        this.payment = payment;
        this.processedAt = processedAt;
        this.status = SubscriptionChargeAttemptStatus.SUCCEEDED;
    }

    public void markFailed(Payment payment, Instant processedAt, String code, String message) {
        this.payment = payment;
        this.processedAt = processedAt;
        this.status = SubscriptionChargeAttemptStatus.FAILED;
        this.failureCode = code;
        this.failureMessage = message;
    }

    public UUID getId() { return id; }
    public CreatorSubscription getSubscription() { return subscription; }
    public Instant getScheduledAt() { return scheduledAt; }
    public Instant getProcessedAt() { return processedAt; }
    public SubscriptionChargeAttemptStatus getStatus() { return status; }
    public Contribution getContribution() { return contribution; }
    public Payment getPayment() { return payment; }
    public String getFailureCode() { return failureCode; }
    public String getFailureMessage() { return failureMessage; }
    public String getIdempotencyKey() { return idempotencyKey; }
}
