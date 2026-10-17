package com.ebp03.plataforma_crowdfunding_backend.auth.service;

import com.ebp03.plataforma_crowdfunding_backend.auth.api.PublicUser;
import com.ebp03.plataforma_crowdfunding_backend.auth.api.UpdateUserProfileRequest;
import com.ebp03.plataforma_crowdfunding_backend.auth.domain.User;
import com.ebp03.plataforma_crowdfunding_backend.auth.repository.UserRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserProfileService {
    private final UserRepository userRepository;

    public UserProfileService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public PublicUser getCurrentUser(User authenticatedUser) {
        User user = findAuthenticatedUser(authenticatedUser.getId());
        return PublicUser.from(user);
    }

    @Transactional
    public PublicUser updateCurrentUser(User authenticatedUser, UpdateUserProfileRequest request) {
        User user = userRepository.findByIdForUpdate(authenticatedUser.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        user.updateName(request.getName());
        return PublicUser.from(user);
    }

    private User findAuthenticatedUser(UUID authenticatedUserId) {
        return userRepository.findById(authenticatedUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
