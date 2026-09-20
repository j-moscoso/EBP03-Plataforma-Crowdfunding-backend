package com.ebp03.plataforma_crowdfunding_backend.auth.api;

import java.time.Instant;

public record AuthResponse(PublicUser user, SessionResponse session) {
    public record SessionResponse(Instant expiresAt) { }
}
