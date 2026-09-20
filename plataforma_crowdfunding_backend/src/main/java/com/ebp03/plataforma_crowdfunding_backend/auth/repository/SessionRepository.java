package com.ebp03.plataforma_crowdfunding_backend.auth.repository;

import com.ebp03.plataforma_crowdfunding_backend.auth.domain.Session;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionRepository extends JpaRepository<Session, UUID> {
    Optional<Session> findByTokenHash(String tokenHash);
}
