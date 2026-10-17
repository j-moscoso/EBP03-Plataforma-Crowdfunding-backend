package com.ebp03.plataforma_crowdfunding_backend.auth.api;

import java.time.Instant;

public final class PasswordResetResponses {
    private PasswordResetResponses() { }
    public record GenericMessage(String message) { }
    public record Validation(boolean valid, Instant expiresAt) { }
    public record Confirmation(String message, boolean loginRequired) { }
}
