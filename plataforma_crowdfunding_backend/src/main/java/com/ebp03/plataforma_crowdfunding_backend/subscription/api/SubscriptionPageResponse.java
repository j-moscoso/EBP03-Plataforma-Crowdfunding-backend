package com.ebp03.plataforma_crowdfunding_backend.subscription.api;

import java.util.List;

public record SubscriptionPageResponse(
        List<SubscriptionResponse> content,
        int page,
        int pageSize,
        long totalElements,
        int totalPages) {
}
