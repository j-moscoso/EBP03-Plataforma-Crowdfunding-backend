package com.ebp03.plataforma_crowdfunding_backend.config;

import com.ebp03.plataforma_crowdfunding_backend.auth.domain.AccountStatus;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.SocialAccount;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.User;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.UserRole;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.VerificationStatus;
import com.ebp03.plataforma_crowdfunding_backend.auth.repository.SocialAccountRepository;
import com.ebp03.plataforma_crowdfunding_backend.auth.repository.UserRepository;
import com.ebp03.plataforma_crowdfunding_backend.auth.service.SessionService;
import java.util.Locale;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Configuration
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class SampleDataSeeder {
    @Bean
    CommandLineRunner sampleData(UserRepository users, SocialAccountRepository socialAccounts,
                                 PasswordEncoder encoder, SessionService sessions) {
        return args -> seed(users, socialAccounts, encoder, sessions);
    }

    @Transactional
    void seed(UserRepository users, SocialAccountRepository socialAccounts, PasswordEncoder encoder, SessionService sessions) {
        String creatorPassword = required("SEED_CREATOR_PASSWORD");
        String sponsorPassword = required("SEED_SPONSOR_PASSWORD");
        User creator = users.findByEmail("example.creator@impulsafund.test").orElseGet(() -> users.save(new User(
                "Example Creator", "example.creator@impulsafund.test", encoder.encode(creatorPassword), UserRole.CREATOR,
                VerificationStatus.VERIFIED, AccountStatus.ACTIVE)));
        User sponsor = users.findByEmail("example.sponsor@impulsafund.test").orElseGet(() -> users.save(new User(
                "Example Sponsor", "example.sponsor@impulsafund.test", encoder.encode(sponsorPassword), UserRole.SPONSOR,
                VerificationStatus.VERIFIED, AccountStatus.ACTIVE)));
        if (socialAccounts.findByProviderAndProviderUserId("google", "seed-google-user-0001").isEmpty()) {
            socialAccounts.save(new SocialAccount(sponsor, "google", "seed-google-user-0001", sponsor.getEmail()));
        }
        SessionService.IssuedSession current = sessions.create(sponsor, false, "seed", "127.0.0.1");
        SessionService.IssuedSession revoked = sessions.create(creator, false, "seed", "127.0.0.1");
        sessions.revoke(revoked.rawToken());
        if (current.rawToken().isBlank()) throw new IllegalStateException("Seed session creation failed");
    }

    private String required(String variable) {
        String value = System.getenv(variable);
        if (value == null || value.isBlank()) throw new IllegalStateException(variable + " must be set when APP_SEED_ENABLED=true");
        return value;
    }
}
