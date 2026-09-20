package com.ebp03.plataforma_crowdfunding_backend.campaign.api;

import com.ebp03.plataforma_crowdfunding_backend.auth.domain.User;
import com.ebp03.plataforma_crowdfunding_backend.campaign.service.CampaignService;
import java.util.UUID;
import org.springframework.data.domain.Page;
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

    public CampaignController(CampaignService campaignService) {
        this.campaignService = campaignService;
    }

    @PostMapping("/drafts")
    public ResponseEntity<CampaignService.DraftResponse> createDraft(@AuthenticationPrincipal User creator,
                                                                    @RequestBody CampaignService.DraftCreateRequest request) {
        return ResponseEntity.status(201).body(campaignService.createDraft(creator, request));
    }

    @GetMapping("/drafts")
    public ResponseEntity<Page<CampaignService.DraftResponse>> listDrafts(@AuthenticationPrincipal User creator,
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
    public ResponseEntity<Page<CampaignService.CampaignSummaryResponse>> listCampaigns(@RequestParam(defaultValue = "0") Integer page,
                                                                                   @RequestParam(defaultValue = "10") Integer pageSize,
                                                                                   @RequestParam(required = false) String category,
                                                                                   @RequestParam(required = false) String status) {
        return ResponseEntity.ok(campaignService.listCampaigns(page, pageSize, category, status));
    }

    @GetMapping("/campaigns/{campaignId}")
    public ResponseEntity<CampaignService.CampaignDetailResponse> getCampaign(@PathVariable UUID campaignId) {
        return ResponseEntity.ok(campaignService.getCampaign(campaignId));
    }

    @PostMapping("/campaigns/{campaignId}/updates")
    public ResponseEntity<CampaignService.CampaignUpdateResponse> createUpdate(@AuthenticationPrincipal User creator,
                                                                             @PathVariable UUID campaignId,
                                                                             @RequestBody CampaignService.CampaignUpdateRequest request) {
        return ResponseEntity.status(201).body(campaignService.createCampaignUpdate(creator, campaignId, request));
    }
}
