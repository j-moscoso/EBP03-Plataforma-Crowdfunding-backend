package com.ebp03.plataforma_crowdfunding_backend.auth.api;

import jakarta.validation.constraints.NotBlank;

public record RegisterRequest(
        @NotBlank String name,
        @NotBlank String email,
        @NotBlank String password,
        @NotBlank String role) {
}
