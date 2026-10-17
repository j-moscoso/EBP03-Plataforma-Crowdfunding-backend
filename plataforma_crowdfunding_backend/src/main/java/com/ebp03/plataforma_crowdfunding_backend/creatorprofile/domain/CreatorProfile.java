package com.ebp03.plataforma_crowdfunding_backend.creatorprofile.domain;

import com.ebp03.plataforma_crowdfunding_backend.auth.domain.User;
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
@Table(name = "creator_profiles")
public class CreatorProfile {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(columnDefinition = "text")
    private String bio;

    @Column(name = "professional_title", length = 120)
    private String professionalTitle;

    @Column(length = 30)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "bank_account_type", nullable = false, length = 30)
    private BankAccountType bankAccountType;

    @Column(name = "bank_name", nullable = false, length = 120)
    private String bankName;

    @Column(name = "bank_account_number", nullable = false, length = 512)
    private String protectedBankAccountNumber;

    @Column(name = "bank_account_holder", nullable = false, length = 160)
    private String bankAccountHolder;

    @Column(name = "bank_account_document", nullable = false, length = 512)
    private String protectedBankAccountDocument;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CreatorProfile() {
    }

    public CreatorProfile(User user, String displayName, String bio, String professionalTitle, String phone,
                          BankAccountType bankAccountType, String bankName, String protectedBankAccountNumber,
                          String bankAccountHolder, String protectedBankAccountDocument) {
        this.id = UUID.randomUUID();
        this.user = user;
        update(displayName, bio, professionalTitle, phone, bankAccountType, bankName,
                protectedBankAccountNumber, bankAccountHolder, protectedBankAccountDocument);
    }

    public void update(String displayName, String bio, String professionalTitle, String phone,
                       BankAccountType bankAccountType, String bankName, String protectedBankAccountNumber,
                       String bankAccountHolder, String protectedBankAccountDocument) {
        this.displayName = displayName;
        this.bio = bio;
        this.professionalTitle = professionalTitle;
        this.phone = phone;
        this.bankAccountType = bankAccountType;
        this.bankName = bankName;
        this.protectedBankAccountNumber = protectedBankAccountNumber;
        this.bankAccountHolder = bankAccountHolder;
        this.protectedBankAccountDocument = protectedBankAccountDocument;
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
    public User getUser() { return user; }
    public String getDisplayName() { return displayName; }
    public String getBio() { return bio; }
    public String getProfessionalTitle() { return professionalTitle; }
    public String getPhone() { return phone; }
    public BankAccountType getBankAccountType() { return bankAccountType; }
    public String getBankName() { return bankName; }
    public String getProtectedBankAccountNumber() { return protectedBankAccountNumber; }
    public String getBankAccountHolder() { return bankAccountHolder; }
    public String getProtectedBankAccountDocument() { return protectedBankAccountDocument; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
