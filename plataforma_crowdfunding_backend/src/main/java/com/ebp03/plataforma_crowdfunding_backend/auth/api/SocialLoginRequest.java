package com.ebp03.plataforma_crowdfunding_backend.auth.api;

import jakarta.validation.constraints.NotBlank;

public record SocialLoginRequest(@NotBlank String provider, @NotBlank String authorizationCode, String role) {
}
