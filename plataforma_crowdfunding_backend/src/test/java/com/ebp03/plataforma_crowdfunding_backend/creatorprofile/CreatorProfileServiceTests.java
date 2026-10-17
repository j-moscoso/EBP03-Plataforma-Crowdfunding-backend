package com.ebp03.plataforma_crowdfunding_backend.creatorprofile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebp03.plataforma_crowdfunding_backend.auth.domain.AccountStatus;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.User;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.UserRole;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.VerificationStatus;
import com.ebp03.plataforma_crowdfunding_backend.auth.repository.UserRepository;
import com.ebp03.plataforma_crowdfunding_backend.creatorprofile.api.CreatorProfileResponse;
import com.ebp03.plataforma_crowdfunding_backend.creatorprofile.api.SaveCreatorProfileRequest;
import com.ebp03.plataforma_crowdfunding_backend.creatorprofile.repository.CreatorProfileLinkRepository;
import com.ebp03.plataforma_crowdfunding_backend.creatorprofile.repository.CreatorProfileRepository;
import com.ebp03.plataforma_crowdfunding_backend.creatorprofile.service.CreatorProfileService;
import jakarta.validation.Validation;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CreatorProfileServiceTests {
    @Test
    void facadeUsesTheProtectionPortAndMapsOnlyMaskedBankValues() {
        UserRepository users = mock(UserRepository.class);
        CreatorProfileRepository profiles = mock(CreatorProfileRepository.class);
        CreatorProfileLinkRepository links = mock(CreatorProfileLinkRepository.class);
        ControlledBankDataProtectionAdapter protection = new ControlledBankDataProtectionAdapter();
        User creator = new User("Creadora", "creator@test.local", "hash",
                UserRole.CREATOR, VerificationStatus.PENDING, AccountStatus.ACTIVE);
        when(users.findByIdForUpdate(creator.getId())).thenReturn(Optional.of(creator));
        when(profiles.findByUser_Id(creator.getId())).thenReturn(Optional.empty());
        when(profiles.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(links.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            CreatorProfileService service = new CreatorProfileService(
                    users, profiles, links, protection, validatorFactory.getValidator());
            CreatorProfileResponse result = service.saveCurrentProfile(creator, new SaveCreatorProfileRequest(
                    "Creadora", null, null, null, "SAVINGS", "Banco",
                    "1234567890", "Titular", "123456789", List.of()));

            assertEquals("******7890", result.bankAccountNumberMasked());
            assertEquals("*******89", result.bankAccountDocumentMasked());
            verify(links).deleteAllForProfile(result.id());
            verify(profiles).flush();
        }
    }
}
