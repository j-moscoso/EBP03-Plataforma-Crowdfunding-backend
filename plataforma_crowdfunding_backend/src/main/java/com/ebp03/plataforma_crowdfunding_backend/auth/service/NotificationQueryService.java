package com.ebp03.plataforma_crowdfunding_backend.auth.service;

import com.ebp03.plataforma_crowdfunding_backend.auth.api.NotificationPageResponse;
import com.ebp03.plataforma_crowdfunding_backend.auth.api.NotificationResponse;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.Notification;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.User;
import com.ebp03.plataforma_crowdfunding_backend.auth.repository.NotificationRepository;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotificationQueryService {
    private final NotificationRepository notificationRepository;

    public NotificationQueryService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional(readOnly = true)
    public NotificationPageResponse getMyNotifications(User authenticatedUser, int page, int pageSize, boolean unreadOnly) {
        if (authenticatedUser == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (page < 0 || pageSize < 1 || pageSize > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION");
        }
        var pageable = PageRequest.of(page, pageSize,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<Notification> notifications = unreadOnly
                ? notificationRepository.findByRecipientUserIdAndReadAtIsNull(authenticatedUser.getId(), pageable)
                : notificationRepository.findByRecipientUserId(authenticatedUser.getId(), pageable);
        List<NotificationResponse> content = notifications.getContent().stream()
                .map(item -> new NotificationResponse(
                        item.getId(), item.getType(), item.getCampaignId(), item.getCampaignUpdateId(),
                        item.getTitle(), item.getBody(), item.getReadAt(), item.getCreatedAt()))
                .toList();
        return new NotificationPageResponse(
                content, page, pageSize, notifications.getTotalElements(), notifications.getTotalPages());
    }
}
