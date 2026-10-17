package com.ebp03.plataforma_crowdfunding_backend.auth.api;

import jakarta.validation.constraints.NotBlank;

public final class PasswordResetRequests {
    private PasswordResetRequests() { }
    public record Request(String email) { }
    public record Validate(@NotBlank String token) { }
    public record Confirm(@NotBlank String token, @NotBlank String newPassword) { }
}
