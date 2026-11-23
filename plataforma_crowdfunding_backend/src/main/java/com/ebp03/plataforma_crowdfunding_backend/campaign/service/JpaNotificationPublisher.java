package com.ebp03.plataforma_crowdfunding_backend.campaign.service;

import com.ebp03.plataforma_crowdfunding_backend.auth.domain.Notification;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.User;
import com.ebp03.plataforma_crowdfunding_backend.auth.repository.NotificationRepository;
import com.ebp03.plataforma_crowdfunding_backend.auth.repository.UserRepository;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.Campaign;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.CampaignUpdate;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.Contribution;
import java.util.List;
import java.util.Locale;
import java.math.RoundingMode;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;
import org.springframework.stereotype.Component;

@Component
public class JpaNotificationPublisher implements NotificationPublisher {
    public static final String CAMPAIGN_UPDATE_PUBLISHED = "CAMPAIGN_UPDATE_PUBLISHED";
    public static final String NEW_CONTRIBUTION = "NEW_CONTRIBUTION";
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public JpaNotificationPublisher(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
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

    @Override
    @Transactional
    public boolean publishNewContribution(Campaign campaign, Contribution contribution) {
        if (campaign == null || contribution == null || contribution.getCampaign() == null
                || !campaign.getId().equals(contribution.getCampaign().getId())) {
            throw new IllegalArgumentException("La campaña y el aporte deben estar asociados");
        }
        var recipientId = campaign.getCreatorId();
        if (notificationRepository.existsByRecipientUserIdAndContributionIdAndType(
                recipientId, contribution.getId(), NEW_CONTRIBUTION)) {
            return false;
        }
        String sponsorName = contribution.isAnonymous()
                ? "Anónimo"
                : userRepository.findById(contribution.getSponsorId())
                        .map(User::getName)
                        .map(String::trim)
                        .filter(name -> !name.isEmpty())
                                .map(JpaNotificationPublisher::escapeAndLimit)
                        .orElse("Anónimo");
        String amount = contribution.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString();
        String currency = contribution.getCurrency().trim().toUpperCase(Locale.ROOT);
        Notification notification = new Notification(
                recipientId,
                campaign.getId(),
                null,
                contribution.getId(),
                NEW_CONTRIBUTION,
                "Nuevo aporte recibido",
                "¡Nuevo aporte! " + sponsorName + " ha aportado $" + amount + " " + currency);
        notificationRepository.saveAndFlush(notification);
        return true;
    }

    private static String escapeAndLimit(String name) {
        StringBuilder escaped = new StringBuilder();
        name.codePoints()
                .filter(codePoint -> !Character.isISOControl(codePoint))
                .mapToObj(Character::toString)
                .map(value -> HtmlUtils.htmlEscape(value, java.nio.charset.StandardCharsets.UTF_8.name()))
                .takeWhile(part -> escaped.length() + part.length() <= 400)
                .forEach(escaped::append);
        return escaped.isEmpty() ? "Anónimo" : escaped.toString();
    }
}
