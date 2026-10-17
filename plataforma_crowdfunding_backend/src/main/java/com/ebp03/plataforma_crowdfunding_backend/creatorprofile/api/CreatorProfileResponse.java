package com.ebp03.plataforma_crowdfunding_backend.creatorprofile.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreatorProfileResponse(
        UUID id,
        UUID userId,
        String displayName,
        String bio,
        String professionalTitle,
        String phone,
        String bankAccountType,
        String bankName,
        String bankAccountNumberMasked,
        String bankAccountHolder,
        String bankAccountDocumentMasked,
        List<CreatorProfileLinkResponse> links,
        Instant createdAt,
        Instant updatedAt) {
}
