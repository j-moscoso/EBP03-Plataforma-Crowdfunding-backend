package com.ebp03.plataforma_crowdfunding_backend.auth.api;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String email, @NotBlank String password, Boolean rememberMe) {
}
