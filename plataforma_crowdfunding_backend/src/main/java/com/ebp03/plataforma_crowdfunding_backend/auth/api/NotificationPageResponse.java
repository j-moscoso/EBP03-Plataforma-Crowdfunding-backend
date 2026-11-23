package com.ebp03.plataforma_crowdfunding_backend.auth.api;

import java.util.List;

public record NotificationPageResponse(
        List<NotificationResponse> content,
        int page,
        int pageSize,
        long totalElements,
        int totalPages,
        long unreadCount) {
    public NotificationPageResponse(
            List<NotificationResponse> content,
            int page,
            int pageSize,
            long totalElements,
            int totalPages) {
        this(content, page, pageSize, totalElements, totalPages, 0);
    }
}
