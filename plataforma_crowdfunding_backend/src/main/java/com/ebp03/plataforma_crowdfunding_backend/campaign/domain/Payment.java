package com.ebp03.plataforma_crowdfunding_backend.campaign.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contribution_id")
    private Contribution contribution;
    @Column(name = "subscription_charge_attempt_id", unique = true)
    private UUID subscriptionChargeAttemptId;
    @Column(nullable = false, length = 40) private String provider;
    @Column(name = "provider_payment_id", unique = true, length = 160) private String providerPaymentId;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal amount;
    @Column(name = "platform_fee", nullable = false, precision = 19, scale = 2) private BigDecimal platformFee;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private PaymentStatus status;
    @Column(name = "failure_code", length = 80) private String failureCode;
    @Column(name = "failure_message", length = 500) private String failureMessage;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "refunded_at") private Instant refundedAt;

    protected Payment() { }
    public Payment(Contribution contribution, String provider, BigDecimal amount, BigDecimal platformFee) {
        this.id = UUID.randomUUID(); this.contribution = contribution; this.provider = provider;
        this.amount = amount; this.platformFee = platformFee; this.status = PaymentStatus.CREATED;
    }
    public Payment(Contribution contribution, String provider, BigDecimal amount, BigDecimal platformFee,
                   UUID subscriptionChargeAttemptId) {
        this(contribution, provider, amount, platformFee);
        this.subscriptionChargeAttemptId = subscriptionChargeAttemptId;
    }
    public Payment(String provider, BigDecimal amount, BigDecimal platformFee, UUID subscriptionChargeAttemptId) {
        this(null, provider, amount, platformFee, subscriptionChargeAttemptId);
    }
    @PrePersist void onCreate() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
    public UUID getId() { return id; }
    public Contribution getContribution() { return contribution; }
    public UUID getSubscriptionChargeAttemptId() { return subscriptionChargeAttemptId; }
    public String getProvider() { return provider; }
    public String getProviderPaymentId() { return providerPaymentId; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getPlatformFee() { return platformFee; }
    public PaymentStatus getStatus() { return status; }
    public String getFailureCode() { return failureCode; }
    public String getFailureMessage() { return failureMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getRefundedAt() { return refundedAt; }
    public void setStatus(PaymentStatus status) { this.status = status; }
    public void setProviderPaymentId(String providerPaymentId) { this.providerPaymentId = providerPaymentId; }
    public void setFailure(String code, String message) { this.failureCode = code; this.failureMessage = message; }
    public void setRefundedAt(Instant refundedAt) { this.refundedAt = refundedAt; }
}