package com.ebp03.plataforma_crowdfunding_backend.auth.repository;

import com.ebp03.plataforma_crowdfunding_backend.auth.domain.Notification;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    Page<Notification> findByRecipientUserId(UUID recipientUserId, Pageable pageable);
    Page<Notification> findByRecipientUserIdAndReadAtIsNull(UUID recipientUserId, Pageable pageable);

    @Modifying
    @Query(value = """
            update notifications
               set read_at = CURRENT_TIMESTAMP
             where id = :notificationId
               and recipient_user_id = :recipientUserId
               and read_at is null
            """, nativeQuery = true)
    int markAsRead(@Param("notificationId") UUID notificationId, @Param("recipientUserId") UUID recipientUserId);
}
