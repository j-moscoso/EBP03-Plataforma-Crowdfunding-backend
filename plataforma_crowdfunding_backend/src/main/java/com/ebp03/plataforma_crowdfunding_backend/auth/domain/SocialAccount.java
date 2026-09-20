package com.ebp03.plataforma_crowdfunding_backend.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "social_accounts", uniqueConstraints = @UniqueConstraint(name = "uk_social_provider_user", columnNames = {"provider", "provider_user_id"}))
public class SocialAccount {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 30)
    private String provider;

    @Column(name = "provider_user_id", nullable = false, length = 255)
    private String providerUserId;

    @Column(name = "email_at_provider", length = 320)
    private String emailAtProvider;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SocialAccount() {
    }

    public SocialAccount(User user, String provider, String providerUserId, String emailAtProvider) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.emailAtProvider = emailAtProvider;
    }

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public String getProvider() { return provider; }
    public String getProviderUserId() { return providerUserId; }
}
