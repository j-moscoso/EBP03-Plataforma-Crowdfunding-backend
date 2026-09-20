package com.ebp03.plataforma_crowdfunding_backend.auth.api;

import com.ebp03.plataforma_crowdfunding_backend.auth.domain.User;
import java.util.UUID;

public record PublicUser(UUID id, String name, String email, String role, String verificationStatus) {
    public static PublicUser from(User user) {
        return new PublicUser(user.getId(), user.getName(), user.getEmail(), user.getRole().name().toLowerCase(), user.getVerificationStatus().name().toLowerCase());
    }
}
