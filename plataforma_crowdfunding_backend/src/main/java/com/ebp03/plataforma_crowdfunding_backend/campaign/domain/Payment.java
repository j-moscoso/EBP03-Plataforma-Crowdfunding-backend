package com.ebp03.plataforma_crowdfunding_backend.campaign.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contribution_id", nullable = false)
    private Contribution contribution;
    @Column(nullable = false, length = 40) private String provider;
    @Column(name = "provider_payment_id", unique = true, length = 160) private String providerPaymentId;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal amount;
    @Column(name = "platform_fee", nullable = false, precision = 19, scale = 2) private BigDecimal platformFee;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private PaymentStatus status;
    @Column(name = "failure_code", length = 80) private String failureCode;
    @Column(name = "failure_message", length = 500) private String failureMessage;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected Payment() { }
    public Payment(Contribution contribution, String provider, BigDecimal amount, BigDecimal platformFee) {
        this.id = UUID.randomUUID(); this.contribution = contribution; this.provider = provider;
        this.amount = amount; this.platformFee = platformFee; this.status = PaymentStatus.CREATED;
    }
    @PrePersist void onCreate() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
    public UUID getId() { return id; }
    public Contribution getContribution() { return contribution; }
    public String getProvider() { return provider; }
    public String getProviderPaymentId() { return providerPaymentId; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getPlatformFee() { return platformFee; }
    public PaymentStatus getStatus() { return status; }
    public String getFailureCode() { return failureCode; }
    public String getFailureMessage() { return failureMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public void setStatus(PaymentStatus status) { this.status = status; }
    public void setProviderPaymentId(String providerPaymentId) { this.providerPaymentId = providerPaymentId; }
    public void setFailure(String code, String message) { this.failureCode = code; this.failureMessage = message; }
}