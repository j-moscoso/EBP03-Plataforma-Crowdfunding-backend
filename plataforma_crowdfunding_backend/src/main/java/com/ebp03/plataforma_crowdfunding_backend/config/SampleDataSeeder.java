package com.ebp03.plataforma_crowdfunding_backend.config;

import com.ebp03.plataforma_crowdfunding_backend.auth.domain.AccountStatus;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.SocialAccount;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.User;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.UserRole;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.VerificationStatus;
import com.ebp03.plataforma_crowdfunding_backend.auth.repository.SocialAccountRepository;
import com.ebp03.plataforma_crowdfunding_backend.auth.repository.UserRepository;
import com.ebp03.plataforma_crowdfunding_backend.auth.service.SessionService;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.Campaign;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.CampaignReward;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.CampaignStatus;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.Contribution;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.ContributionStatus;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.Payment;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.PaymentEvent;
import com.ebp03.plataforma_crowdfunding_backend.campaign.domain.PaymentStatus;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.CampaignRepository;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.ContributionRepository;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.PaymentEventRepository;
import com.ebp03.plataforma_crowdfunding_backend.campaign.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
                                 PasswordEncoder encoder, SessionService sessions,
                                 CampaignRepository campaigns, ContributionRepository contributions,
                                 PaymentRepository payments, PaymentEventRepository paymentEvents) {
        return args -> seed(users, socialAccounts, encoder, sessions, campaigns, contributions, payments, paymentEvents);
    }

    @Transactional
    void seed(UserRepository users, SocialAccountRepository socialAccounts, PasswordEncoder encoder,
              SessionService sessions, CampaignRepository campaigns, ContributionRepository contributions,
              PaymentRepository payments, PaymentEventRepository paymentEvents) {
        String creatorPassword = required("SEED_CREATOR_PASSWORD");
        String sponsorPassword = required("SEED_SPONSOR_PASSWORD");

        User creator = users.findByEmail("example.creator@impulsafund.test").orElseGet(() -> users.save(new User(
                "Example Creator", "example.creator@impulsafund.test", encoder.encode(creatorPassword), UserRole.CREATOR,
                VerificationStatus.VERIFIED, AccountStatus.ACTIVE)));
        if (creator.getVerificationStatus() != VerificationStatus.VERIFIED) {
            creator.setVerificationStatus(VerificationStatus.VERIFIED);
            users.save(creator);
        }

        User sponsor = users.findByEmail("example.sponsor@impulsafund.test").orElseGet(() -> users.save(new User(
                "Example Sponsor", "example.sponsor@impulsafund.test", encoder.encode(sponsorPassword), UserRole.SPONSOR,
                VerificationStatus.VERIFIED, AccountStatus.ACTIVE)));
        if (socialAccounts.findByProviderAndProviderUserId("google", "seed-google-user-0001").isEmpty()) {
            socialAccounts.save(new SocialAccount(sponsor, "google", "seed-google-user-0001", sponsor.getEmail()));
        }

        Campaign existingCampaign = campaigns.findAll().stream().filter(item -> item.getTitle().equals("Bosques de la vida")).findFirst().orElse(null);
        Campaign campaignToSeed = existingCampaign;
        if (campaignToSeed == null) {
            Campaign createdCampaign = campaigns.save(new Campaign(
                    creator.getId(),
                    "Bosques de la vida",
                    "Proyecto para restaurar áreas verdes y proteger el hábitat local.",
                    new BigDecimal("30000.00"),
                    Instant.now().plusSeconds(45L * 24L * 60L * 60L),
                    "Medio ambiente",
                    "https://example.com/media/bosques.jpg",
                    CampaignStatus.ACTIVE));
            createdCampaign.setRewards(List.of(
                    new CampaignReward(createdCampaign, "Agradecimiento", "Reconocimiento digital", new BigDecimal("10.00"), null, 0),
                    new CampaignReward(createdCampaign, "Kit verde", "Kit de apoyo del proyecto", new BigDecimal("50.00"), 100, 0),
                    new CampaignReward(createdCampaign, "Membresía especial", "Acceso exclusivo", new BigDecimal("150.00"), null, 0)
            ));
            campaignToSeed = campaigns.save(createdCampaign);
        }

        final UUID seedCampaignId = campaignToSeed.getId();
        boolean alreadyRaised = contributions.findAll().stream().anyMatch(item -> item.getCampaign().getId().equals(seedCampaignId) && item.getStatus() == ContributionStatus.CONFIRMED);
        if (!alreadyRaised) {
                CampaignReward reward = campaignToSeed.getRewards().stream().filter(item -> item.getMinimumAmount().compareTo(new BigDecimal("50.00")) == 0).findFirst().orElse(null);
                Contribution confirmed = contributions.save(new Contribution(campaignToSeed, sponsor.getId(), reward, new BigDecimal("75.00"), "USD", ContributionStatus.CONFIRMED, "seed-confirmed-75"));
                confirmed.setConfirmedAt(Instant.now());
                if (reward != null) reward.setClaimedQuantity(1);
                Payment succeeded = payments.save(new Payment(confirmed, "simulated", confirmed.getAmount(), new BigDecimal("3.75")));
                succeeded.setStatus(PaymentStatus.SUCCEEDED);
                succeeded.setProviderPaymentId("seed-payment-success");
                Payment failedPayment = payments.save(new Payment(
                    contributions.save(new Contribution(campaignToSeed, sponsor.getId(), null, new BigDecimal("25.00"), "USD", ContributionStatus.FAILED, "seed-failed-25")),
                    "simulated", new BigDecimal("25.00"), new BigDecimal("1.25")));
                failedPayment.setStatus(PaymentStatus.FAILED);
                failedPayment.setFailure("SIMULATED_FAILURE", "Payment provider failure");
                paymentEvents.save(new PaymentEvent(succeeded, "seed-webhook-success", "payment.succeeded", "seed-payload-hash"));
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
