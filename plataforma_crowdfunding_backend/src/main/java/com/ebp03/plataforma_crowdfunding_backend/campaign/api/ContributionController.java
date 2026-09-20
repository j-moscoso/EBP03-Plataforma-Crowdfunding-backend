package com.ebp03.plataforma_crowdfunding_backend.campaign.api;

import com.ebp03.plataforma_crowdfunding_backend.auth.domain.User;
import com.ebp03.plataforma_crowdfunding_backend.campaign.service.ContributionService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ContributionController {
    private final ContributionService service;
    public ContributionController(ContributionService service) { this.service = service; }
    @PostMapping("/campaigns/{campaignId}/contributions")
    public ResponseEntity<?> create(@AuthenticationPrincipal User user, @PathVariable UUID campaignId, @RequestHeader("Idempotency-Key") String key, @RequestBody ContributionService.CreateRequest request) { return ResponseEntity.status(201).body(service.create(user, campaignId, request, key)); }
    @GetMapping("/contributions/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal User user, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int pageSize, @RequestParam(required = false) String status) { return ResponseEntity.ok(service.list(user, page, pageSize, status)); }
    @GetMapping("/contributions/{contributionId}")
    public ResponseEntity<?> detail(@AuthenticationPrincipal User user, @PathVariable UUID contributionId) { return ResponseEntity.ok(service.detail(user, contributionId)); }
    @PostMapping("/contributions/{contributionId}/retry")
    public ResponseEntity<?> retry(@AuthenticationPrincipal User user, @PathVariable UUID contributionId, @RequestHeader("Idempotency-Key") String key, @RequestBody ContributionService.RetryRequest request) { return ResponseEntity.ok(service.retry(user, contributionId, request.paymentMethodId(), key)); }
    @PostMapping("/payments/webhook")
    public ResponseEntity<Void> webhook(@RequestHeader(value = "X-Payment-Signature", required = false) String signature, @RequestHeader("X-Provider-Event-Id") String eventId, @RequestParam UUID paymentId, @RequestParam String eventType, @RequestParam String status, @RequestBody String payload) { service.webhook(signature, payload, eventId, paymentId, eventType, status); return ResponseEntity.ok().build(); }
}