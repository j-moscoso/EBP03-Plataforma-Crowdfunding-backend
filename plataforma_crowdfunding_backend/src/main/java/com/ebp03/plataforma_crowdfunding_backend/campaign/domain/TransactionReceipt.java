package com.ebp03.plataforma_crowdfunding_backend.campaign.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transaction_receipts")
public class TransactionReceipt {
    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contribution_id", nullable = false, unique = true)
    private Contribution contribution;

    @Column(name = "reference_code", nullable = false, unique = true, length = 80)
    private String referenceCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionReceiptStatus status;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    protected TransactionReceipt() {
    }

    public TransactionReceipt(Contribution contribution, String referenceCode, TransactionReceiptStatus status) {
        this.id = UUID.randomUUID();
        this.contribution = contribution;
        this.referenceCode = referenceCode;
        this.status = status;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        issuedAt = now;
        updatedAt = now;
        if (status == TransactionReceiptStatus.REFUNDED) {
            refundedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
        if (status == TransactionReceiptStatus.REFUNDED && refundedAt == null) {
            refundedAt = updatedAt;
        }
    }

    public UUID getId() { return id; }
    public Contribution getContribution() { return contribution; }
    public String getReferenceCode() { return referenceCode; }
    public TransactionReceiptStatus getStatus() { return status; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getRefundedAt() { return refundedAt; }

    public void setStatus(TransactionReceiptStatus status) { this.status = status; }
}
