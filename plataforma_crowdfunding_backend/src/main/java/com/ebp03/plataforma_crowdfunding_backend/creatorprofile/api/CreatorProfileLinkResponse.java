package com.ebp03.plataforma_crowdfunding_backend.creatorprofile.api;

import java.util.UUID;

public record CreatorProfileLinkResponse(
        UUID id,
        String linkType,
        String label,
        String url,
        int sortOrder) {
}
