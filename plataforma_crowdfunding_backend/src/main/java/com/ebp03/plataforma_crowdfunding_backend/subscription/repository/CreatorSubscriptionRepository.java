package com.ebp03.plataforma_crowdfunding_backend.subscription.repository;

import com.ebp03.plataforma_crowdfunding_backend.subscription.domain.CreatorSubscription;
import com.ebp03.plataforma_crowdfunding_backend.subscription.domain.CreatorSubscriptionStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CreatorSubscriptionRepository extends JpaRepository<CreatorSubscription, UUID> {
    Page<CreatorSubscription> findBySponsorId(UUID sponsorId, Pageable pageable);

    Optional<CreatorSubscription> findBySponsorIdAndCreatorIdAndStatus(
            UUID sponsorId, UUID creatorId, CreatorSubscriptionStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CreatorSubscription s where s.id = :id")
    Optional<CreatorSubscription> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select s.id from CreatorSubscription s
            where s.status = :status and s.nextChargeAt <= :now
            order by s.nextChargeAt, s.id
            """)
    List<UUID> findDueIds(
            @Param("status") CreatorSubscriptionStatus status,
            @Param("now") Instant now,
            Pageable pageable);
}
