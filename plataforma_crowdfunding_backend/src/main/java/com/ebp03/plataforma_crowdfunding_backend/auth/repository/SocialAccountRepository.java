package com.ebp03.plataforma_crowdfunding_backend.auth.repository;

import com.ebp03.plataforma_crowdfunding_backend.auth.domain.SocialAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, UUID> {
    Optional<SocialAccount> findByProviderAndProviderUserId(String provider, String providerUserId);
}
