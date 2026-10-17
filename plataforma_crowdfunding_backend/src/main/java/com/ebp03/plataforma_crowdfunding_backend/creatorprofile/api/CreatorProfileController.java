package com.ebp03.plataforma_crowdfunding_backend.creatorprofile.api;

import com.ebp03.plataforma_crowdfunding_backend.auth.domain.User;
import com.ebp03.plataforma_crowdfunding_backend.creatorprofile.service.CreatorProfileService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/creators/me/profile")
public class CreatorProfileController {
    private final CreatorProfileService creatorProfileService;

    public CreatorProfileController(CreatorProfileService creatorProfileService) {
        this.creatorProfileService = creatorProfileService;
    }

    @GetMapping
    public CreatorProfileResponse getMyProfile(@AuthenticationPrincipal User user) {
        return creatorProfileService.getCurrentProfile(user);
    }

    @PutMapping
    public SaveCreatorProfileResponse saveMyProfile(@AuthenticationPrincipal User user,
                                                    @RequestBody SaveCreatorProfileRequest request) {
        return new SaveCreatorProfileResponse(
                "Perfil guardado", creatorProfileService.saveCurrentProfile(user, request));
    }
}
