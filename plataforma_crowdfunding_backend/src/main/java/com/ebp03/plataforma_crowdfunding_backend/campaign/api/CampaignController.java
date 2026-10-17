package com.ebp03.plataforma_crowdfunding_backend.campaign.api;

import com.ebp03.plataforma_crowdfunding_backend.auth.domain.User;
import com.ebp03.plataforma_crowdfunding_backend.campaign.service.CampaignService;
import com.ebp03.plataforma_crowdfunding_backend.campaign.service.CampaignUpdatePublicationMediator;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CampaignController {
    private final CampaignService campaignService;
    private final CampaignUpdatePublicationMediator updatePublicationMediator;

    public CampaignController(CampaignService campaignService,
                              CampaignUpdatePublicationMediator updatePublicationMediator) {
        this.campaignService = campaignService;
        this.updatePublicationMediator = updatePublicationMediator;
    }

    @PostMapping("/drafts")
    public ResponseEntity<CampaignService.DraftResponse> createDraft(@AuthenticationPrincipal User creator,
                                                                    @RequestBody CampaignService.DraftCreateRequest request) {
        return ResponseEntity.status(201).body(campaignService.createDraft(creator, request));
    }

    @GetMapping("/drafts")
    public ResponseEntity<CampaignService.PageResponse<CampaignService.DraftResponse>> listDrafts(@AuthenticationPrincipal User creator,
                                                                          @RequestParam(defaultValue = "0") int page,
                                                                          @RequestParam(defaultValue = "10") int pageSize) {
        return ResponseEntity.ok(campaignService.listDrafts(creator, page, pageSize));
    }

    @GetMapping("/drafts/{draftId}")
    public ResponseEntity<CampaignService.DraftResponse> getDraft(@AuthenticationPrincipal User creator,
                                                                 @PathVariable UUID draftId) {
        return ResponseEntity.ok(campaignService.getDraft(creator, draftId));
    }

    @PutMapping("/drafts/{draftId}")
    public ResponseEntity<CampaignService.DraftResponse> updateDraft(@AuthenticationPrincipal User creator,
                                                                    @PathVariable UUID draftId,
                                                                    @RequestBody CampaignService.DraftUpdateRequest request) {
        return ResponseEntity.ok(campaignService.updateDraft(creator, draftId, request));
    }

    @DeleteMapping("/drafts/{draftId}")
    public ResponseEntity<Void> deleteDraft(@AuthenticationPrincipal User creator,
                                           @PathVariable UUID draftId) {
        campaignService.deleteDraft(creator, draftId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/drafts/{draftId}/publish")
    public ResponseEntity<CampaignService.CampaignDetailResponse> publishDraft(@AuthenticationPrincipal User creator,
                                                                              @PathVariable UUID draftId) {
        return ResponseEntity.status(201).body(campaignService.publishDraft(creator, draftId));
    }

    @GetMapping("/campaigns")
    public ResponseEntity<CampaignService.PageResponse<CampaignService.CampaignSummaryResponse>> listCampaigns(@RequestParam(defaultValue = "0") Integer page,
                                                                                   @RequestParam(defaultValue = "10") Integer pageSize,
                                                                                   @RequestParam(required = false) String category,
                                                                                   @RequestParam(required = false) String status) {
        return ResponseEntity.ok(campaignService.listCampaigns(page, pageSize, category, status));
    }

    @GetMapping("/campaigns/mine")
    public ResponseEntity<CampaignService.PageResponse<CampaignService.CampaignSummaryResponse>> listMyCampaigns(
            @AuthenticationPrincipal User creator,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(campaignService.listMyCampaigns(creator, page, pageSize, status));
    }

    @GetMapping("/campaigns/{campaignId}")
    public ResponseEntity<CampaignService.CampaignDetailResponse> getCampaign(@PathVariable UUID campaignId) {
        return ResponseEntity.ok(campaignService.getCampaign(campaignId));
    }

    @GetMapping("/campaigns/{campaignId}/progress")
    public ResponseEntity<CampaignService.ProgressResponse> getProgress(@PathVariable UUID campaignId) {
        return ResponseEntity.ok(campaignService.getProgress(campaignId));
    }

    @GetMapping("/campaigns/{campaignId}/updates")
    public ResponseEntity<CampaignUpdateWallResponse> getCampaignUpdates(
            @PathVariable UUID campaignId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ResponseEntity.ok(updatePublicationMediator.getWall(campaignId, page, pageSize));
    }

    @PostMapping("/campaigns/{campaignId}/updates")
    public ResponseEntity<CampaignUpdatePublicationResponse> createUpdate(
            @AuthenticationPrincipal User creator,
            @PathVariable UUID campaignId,
            @RequestBody CampaignUpdatePublicationRequest request) {
        return ResponseEntity.status(201)
                .body(updatePublicationMediator.publish(creator, campaignId, request));
    }
}
