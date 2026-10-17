package com.ebp03.plataforma_crowdfunding_backend.campaign.service;

import com.ebp03.plataforma_crowdfunding_backend.auth.domain.Notification;
import com.ebp03.plataforma_crowdfunding_backend.auth.repository.NotificationRepository;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.Campaign;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.CampaignUpdate;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class JpaNotificationPublisher implements NotificationPublisher {
    public static final String CAMPAIGN_UPDATE_PUBLISHED = "CAMPAIGN_UPDATE_PUBLISHED";
    private final NotificationRepository notificationRepository;

    public JpaNotificationPublisher(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    public int publishCampaignUpdate(Campaign campaign, CampaignUpdate update, List<java.util.UUID> recipientIds) {
        List<Notification> notifications = recipientIds.stream()
                .map(recipientId -> new Notification(
                        recipientId,
                        campaign.getId(),
                        update.getId(),
                        CAMPAIGN_UPDATE_PUBLISHED,
                        "Actualización de campaña",
                        "Se publicó una actualización: " + update.getTitle()))
                .toList();
        notificationRepository.saveAll(notifications);
        notificationRepository.flush();
        return notifications.size();
    }
}
