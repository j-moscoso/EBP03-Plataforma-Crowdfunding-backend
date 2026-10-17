package com.ebp03.plataforma_crowdfunding_backend.auth.repository;

import com.ebp03.plataforma_crowdfunding_backend.auth.domain.Session;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SessionRepository extends JpaRepository<Session, UUID> {
    Optional<Session> findByTokenHash(String tokenHash);
    @Modifying
    @Query(value = "update sessions set revoked_at = CURRENT_TIMESTAMP where user_id = :userId and revoked_at is null", nativeQuery = true)
    int revokeAllForUser(UUID userId);
}
