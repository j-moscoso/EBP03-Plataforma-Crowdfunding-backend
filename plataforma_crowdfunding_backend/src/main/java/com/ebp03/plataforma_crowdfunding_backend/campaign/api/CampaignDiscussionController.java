package com.ebp03.plataforma_crowdfunding_backend.campaign.api;

import com.ebp03.plataforma_crowdfunding_backend.auth.domain.User;
import com.ebp03.plataforma_crowdfunding_backend.campaign.service.CampaignDiscussionService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/campaigns")
public class CampaignDiscussionController {
    private final CampaignDiscussionService discussionService;

    public CampaignDiscussionController(CampaignDiscussionService discussionService) {
        this.discussionService = discussionService;
    }

    @GetMapping("/{campaignId}/discussion")
    public CampaignDiscussionService.DiscussionPageResponse getDiscussion(
            @PathVariable UUID campaignId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return discussionService.getDiscussion(campaignId, page, pageSize);
    }

    @PostMapping("/{campaignId}/discussion/comments")
    public ResponseEntity<CampaignDiscussionService.CommentResponse> publishQuestion(
            @AuthenticationPrincipal User user,
            @PathVariable UUID campaignId,
            @RequestBody(required = false) CampaignDiscussionRequest request) {
        return ResponseEntity.status(201).body(discussionService.publishQuestion(user, campaignId, request));
    }

    @PostMapping("/{campaignId}/discussion/comments/{commentId}/replies")
    public ResponseEntity<CampaignDiscussionService.CommentResponse> reply(
            @AuthenticationPrincipal User user,
            @PathVariable UUID campaignId,
            @PathVariable UUID commentId,
            @RequestBody(required = false) CampaignDiscussionRequest request) {
        return ResponseEntity.status(201).body(discussionService.reply(user, campaignId, commentId, request));
    }

    @PatchMapping("/{campaignId}/discussion/comments/{commentId}/faq")
    public CampaignDiscussionService.CommentResponse convertToFaq(
            @AuthenticationPrincipal User user,
            @PathVariable UUID campaignId,
            @PathVariable UUID commentId,
            @RequestBody(required = false) CampaignFaqRequest request) {
        return discussionService.convertToFaq(user, campaignId, commentId, request);
    }

    @GetMapping("/{campaignId}/faq")
    public CampaignDiscussionService.FaqPageResponse getFaq(
            @PathVariable UUID campaignId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return discussionService.getFaq(campaignId, page, pageSize);
    }
}
