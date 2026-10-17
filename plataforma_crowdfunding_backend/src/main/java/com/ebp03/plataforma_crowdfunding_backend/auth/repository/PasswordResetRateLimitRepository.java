package com.ebp03.plataforma_crowdfunding_backend.auth.repository;

import com.ebp03.plataforma_crowdfunding_backend.auth.domain.PasswordResetRateLimit;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PasswordResetRateLimitRepository extends JpaRepository<PasswordResetRateLimit, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PasswordResetRateLimit r where r.rateKey = :rateKey")
    Optional<PasswordResetRateLimit> findForUpdate(@Param("rateKey") String rateKey);
}
