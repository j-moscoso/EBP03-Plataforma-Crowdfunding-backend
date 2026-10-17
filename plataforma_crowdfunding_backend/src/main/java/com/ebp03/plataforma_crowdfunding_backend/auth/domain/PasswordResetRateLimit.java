package com.ebp03.plataforma_crowdfunding_backend.auth.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "password_reset_rate_limits")
public class PasswordResetRateLimit {
    @Id
    private UUID id;
    @Column(name = "user_id")
    private UUID userId;
    @Column(name = "ip_address", nullable = false, length = 64)
    private String ipAddress;
    @Column(name = "rate_key", nullable = false, unique = true, length = 320)
    private String rateKey;
    @Column(name = "window_started_at", nullable = false)
    private Instant windowStartedAt;
    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;
    @Column(name = "blocked_until")
    private Instant blockedUntil;
    @Column(name = "last_event_at", nullable = false)
    private Instant lastEventAt;

    protected PasswordResetRateLimit() { }

    public PasswordResetRateLimit(UUID userId, String ipAddress, String rateKey, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.ipAddress = ipAddress;
        this.rateKey = rateKey;
        this.windowStartedAt = now;
        this.lastEventAt = now;
    }

    public UUID getId() { return id; }
    public int getFailedAttempts() { return failedAttempts; }
    public Instant getWindowStartedAt() { return windowStartedAt; }
    public Instant getBlockedUntil() { return blockedUntil; }
    public void resetWindow(Instant now) {
        windowStartedAt = now;
        failedAttempts = 0;
        blockedUntil = null;
    }
    public void registerFailure(Instant now, int limit, Instant blockedUntil) {
        failedAttempts++;
        lastEventAt = now;
        if (failedAttempts >= limit) this.blockedUntil = blockedUntil;
    }
    public boolean isBlocked(Instant now) { return blockedUntil != null && blockedUntil.isAfter(now); }
    public void registerEvent(Instant now) { lastEventAt = now; }
}
