package com.ebp03.plataforma_crowdfunding_backend.subscription.api;

import com.ebp03.plataforma_crowdfunding_backend.auth.domain.User;
import com.ebp03.plataforma_crowdfunding_backend.subscription.service.SubscriptionService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {
    private final SubscriptionService service;

    public SubscriptionController(SubscriptionService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionResponse create(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreateSubscriptionRequest request) {
        return service.create(user, request);
    }

    @GetMapping("/me")
    public SubscriptionPageResponse mine(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return service.getMine(user, page, pageSize);
    }

    @PostMapping("/{subscriptionId}/cancel")
    public SubscriptionResponse cancel(
            @AuthenticationPrincipal User user,
            @PathVariable UUID subscriptionId) {
        return service.cancel(user, subscriptionId);
    }
}
