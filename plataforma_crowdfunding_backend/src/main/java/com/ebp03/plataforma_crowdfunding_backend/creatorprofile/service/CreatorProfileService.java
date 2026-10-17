package com.ebp03.plataforma_crowdfunding_backend.creatorprofile.service;

import com.ebp03.plataforma_crowdfunding_backend.auth.api.ValidationErrorDetail;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.User;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.UserRole;
import com.ebp03.plataforma_crowdfunding_backend.auth.repository.UserRepository;
import com.ebp03.plataforma_crowdfunding_backend.creatorprofile.api.CreatorProfileLinkRequest;
import com.ebp03.plataforma_crowdfunding_backend.creatorprofile.api.CreatorProfileLinkResponse;
import com.ebp03.plataforma_crowdfunding_backend.creatorprofile.api.CreatorProfileResponse;
import com.ebp03.plataforma_crowdfunding_backend.creatorprofile.api.SaveCreatorProfileRequest;
import com.ebp03.plataforma_crowdfunding_backend.creatorprofile.domain.BankAccountType;
import com.ebp03.plataforma_crowdfunding_backend.creatorprofile.domain.CreatorProfile;
import com.ebp03.plataforma_crowdfunding_backend.creatorprofile.domain.CreatorProfileLink;
import com.ebp03.plataforma_crowdfunding_backend.creatorprofile.domain.CreatorProfileLinkType;
import com.ebp03.plataforma_crowdfunding_backend.creatorprofile.repository.CreatorProfileLinkRepository;
import com.ebp03.plataforma_crowdfunding_backend.creatorprofile.repository.CreatorProfileRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CreatorProfileService {
    private final UserRepository userRepository;
    private final CreatorProfileRepository profileRepository;
    private final CreatorProfileLinkRepository linkRepository;
    private final BankDataProtectionPort bankDataProtection;
    private final Validator validator;

    public CreatorProfileService(UserRepository userRepository,
                                 CreatorProfileRepository profileRepository,
                                 CreatorProfileLinkRepository linkRepository,
                                 BankDataProtectionPort bankDataProtection,
                                 Validator validator) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.linkRepository = linkRepository;
        this.bankDataProtection = bankDataProtection;
        this.validator = validator;
    }

    @Transactional(readOnly = true)
    public CreatorProfileResponse getCurrentProfile(User authenticatedUser) {
        User user = findAuthenticatedCreator(authenticatedUser);
        CreatorProfile profile = profileRepository.findByUser_Id(user.getId()).orElse(null);
        if (profile == null) return emptyProfile(user);
        List<CreatorProfileLink> links = linkRepository.findByProfile_IdOrderBySortOrderAscIdAsc(profile.getId());
        return toResponse(profile, links);
    }

    @Transactional
    public CreatorProfileResponse saveCurrentProfile(User authenticatedUser, SaveCreatorProfileRequest request) {
        User user = lockAuthenticatedCreator(authenticatedUser);
        validateRequest(request);
        validateUniqueLinks(request.links());

        String protectedAccountNumber = bankDataProtection.encrypt(request.bankAccountNumber());
        String protectedDocument = bankDataProtection.encrypt(request.bankAccountDocument());
        CreatorProfile profile = profileRepository.findByUser_Id(user.getId()).orElse(null);
        String displayName = request.displayName().strip();
        String bio = normalizeOptionalText(request.bio());
        String professionalTitle = normalizeOptionalText(request.professionalTitle());
        String phone = normalizeOptionalText(request.phone());
        String bankName = request.bankName().strip();
        String accountHolder = request.bankAccountHolder().strip();
        BankAccountType accountType = BankAccountType.valueOf(request.bankAccountType());

        if (profile == null) {
            profile = profileRepository.saveAndFlush(new CreatorProfile(
                    user, displayName, bio, professionalTitle, phone, accountType,
                    bankName, protectedAccountNumber, accountHolder, protectedDocument));
        } else {
            profile.update(displayName, bio, professionalTitle, phone, accountType,
                    bankName, protectedAccountNumber, accountHolder, protectedDocument);
        }

        linkRepository.deleteAllForProfile(profile.getId());
        List<CreatorProfileLinkRequest> orderedRequests = orderedLinks(request.links());
        List<CreatorProfileLink> savedLinks = new ArrayList<>();
        for (int index = 0; index < orderedRequests.size(); index++) {
            CreatorProfileLinkRequest link = orderedRequests.get(index);
            savedLinks.add(new CreatorProfileLink(
                    profile,
                    CreatorProfileLinkType.valueOf(link.linkType()),
                    normalizeOptionalText(link.label()),
                    link.url(),
                    index));
        }
        savedLinks = linkRepository.saveAll(savedLinks);
        profileRepository.flush();
        return toResponse(profile, savedLinks);
    }

    private User findAuthenticatedCreator(User authenticatedUser) {
        if (authenticatedUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        User user = userRepository.findById(authenticatedUser.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        requireCreator(user);
        return user;
    }

    private User lockAuthenticatedCreator(User authenticatedUser) {
        if (authenticatedUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        User user = userRepository.findByIdForUpdate(authenticatedUser.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        requireCreator(user);
        return user;
    }

    private void requireCreator(User user) {
        if (user.getRole() != UserRole.CREATOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "CREATOR_ROLE_REQUIRED");
        }
    }

    private CreatorProfileResponse emptyProfile(User user) {
        return new CreatorProfileResponse(
                null, user.getId(), user.getName(), null, null, null, null,
                null, null, null, null, List.of(), null, null);
    }

    private CreatorProfileResponse toResponse(CreatorProfile profile, List<CreatorProfileLink> links) {
        List<CreatorProfileLinkResponse> linkResponses = links.stream()
                .sorted(Comparator.comparingInt(CreatorProfileLink::getSortOrder))
                .map(link -> new CreatorProfileLinkResponse(
                        link.getId(), link.getLinkType().name(), link.getLabel(),
                        link.getUrl(), link.getSortOrder()))
                .toList();
        return new CreatorProfileResponse(
                profile.getId(),
                profile.getUser().getId(),
                profile.getDisplayName(),
                profile.getBio(),
                profile.getProfessionalTitle(),
                profile.getPhone(),
                profile.getBankAccountType().name(),
                profile.getBankName(),
                bankDataProtection.mask(profile.getProtectedBankAccountNumber(), 4),
                profile.getBankAccountHolder(),
                bankDataProtection.mask(profile.getProtectedBankAccountDocument(), 2),
                linkResponses,
                profile.getCreatedAt(),
                profile.getUpdatedAt());
    }

    private void validateUniqueLinks(List<CreatorProfileLinkRequest> links) {
        Set<String> urls = new HashSet<>();
        List<ValidationErrorDetail> details = new ArrayList<>();
        for (int index = 0; index < links.size(); index++) {
            if (!urls.add(links.get(index).url())) {
                details.add(new ValidationErrorDetail(
                        "links[" + index + "].url", "DUPLICATE", "No repitas la misma URL."));
            }
        }
        if (!details.isEmpty()) throw new CreatorProfileValidationException(details);
    }

    private void validateRequest(SaveCreatorProfileRequest request) {
        if (request == null) {
            throw new CreatorProfileValidationException(List.of(
                    new ValidationErrorDetail("profile", "REQUIRED", "El perfil es obligatorio.")));
        }
        List<ValidationErrorDetail> details = validator.validate(request).stream()
                .map(this::toValidationDetail)
                .sorted(Comparator.comparing(ValidationErrorDetail::field)
                        .thenComparing(ValidationErrorDetail::code))
                .toList();
        if (!details.isEmpty()) throw new CreatorProfileValidationException(details);
    }

    private ValidationErrorDetail toValidationDetail(ConstraintViolation<SaveCreatorProfileRequest> violation) {
        String field = violation.getPropertyPath().toString();
        if (!field.startsWith("links[")) field = "profile." + field;
        String code = switch (violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName()) {
            case "NotBlank", "NotNull" -> "REQUIRED";
            case "Size" -> "LENGTH";
            case "ValidProfileUrl" -> "INVALID_URL";
            case "Pattern", "Min" -> "INVALID_FORMAT";
            default -> "INVALID";
        };
        return new ValidationErrorDetail(field, code, violation.getMessage());
    }

    private List<CreatorProfileLinkRequest> orderedLinks(List<CreatorProfileLinkRequest> links) {
        return java.util.stream.IntStream.range(0, links.size())
                .boxed()
                .sorted(Comparator.comparingInt((Integer index) -> links.get(index).sortOrder() == null
                                ? index : links.get(index).sortOrder())
                        .thenComparingInt(Integer::intValue))
                .map(links::get)
                .toList();
    }

    private String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
