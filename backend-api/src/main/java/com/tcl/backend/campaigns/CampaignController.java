package com.tcl.backend.campaigns;

import com.tcl.backend.common.PageResponse;
import com.tcl.backend.security.AuthenticatedAccount;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// REST controller for campaign APIs.
// It receives HTTP requests and delegates business logic to CampaignService.
@RestController
public class CampaignController {

    // Service containing campaign business logic.
    private final CampaignService campaignService;

    // Constructor injection: Spring provides the CampaignService bean.
    public CampaignController(CampaignService campaignService) {
        this.campaignService = campaignService;
    }

    // Public endpoint: GET /api/v1/campaigns?status=&q=&sort=&page=&limit=
    @GetMapping("/api/v1/campaigns")
    PageResponse<CampaignResponse> search(
            // Optional query parameter, for example ?status=OPEN.
            @RequestParam(required = false) String status,
            // Optional search text, for example ?q=school.
            @RequestParam(required = false) String q,
            // Optional sort text, for example ?sort=title,asc.
            @RequestParam(required = false) String sort,
            // If page is missing, use page 1.
            @RequestParam(defaultValue = "1") int page,
            // If limit is missing, use 10 items per page.
            @RequestParam(defaultValue = "10") int limit
    ) {
        return campaignService.search(status, q, sort, page, limit);
    }

    // Public endpoint: GET /api/v1/campaigns/{id}
    // @PathVariable reads {id} from the URL and converts it to UUID.
    @GetMapping("/api/v1/campaigns/{id}")
    CampaignResponse get(@PathVariable UUID id) {
        return campaignService.get(id);
    }

    // Public endpoint: GET /api/v1/campaigns/{id}/donors
    @GetMapping("/api/v1/campaigns/{id}/donors")
    List<CampaignDonorResponse> donors(@PathVariable UUID id) {
        return campaignService.donors(id);
    }

    // Protected endpoint: POST /api/v1/campaigns
    // SecurityConfig requires authentication because this is not a public GET campaign route.
    @PostMapping("/api/v1/campaigns")
    // Successful creation returns HTTP 201 Created.
    @ResponseStatus(HttpStatus.CREATED)
    CampaignResponse create(
            // @RequestBody converts JSON to CreateCampaignRequest.
            // @Valid runs validation annotations on the request DTO.
            @Valid @RequestBody CreateCampaignRequest request,
            // Spring Security fills this from the authenticated JWT principal.
            @AuthenticationPrincipal AuthenticatedAccount principal
    ) {
        return campaignService.create(request, principal);
    }

    // Admin endpoint: PUT /api/v1/admin/campaigns/{id}/approve
    // SecurityConfig requires ROLE_ADMIN for /api/v1/admin/**.
    @PutMapping("/api/v1/admin/campaigns/{id}/approve")
    CampaignActionResponse approve(
            @PathVariable UUID id,
            // Needed so service can verify/load AdminProfile.
            @AuthenticationPrincipal AuthenticatedAccount principal
    ) {
        return campaignService.approve(id, principal);
    }

    // Admin endpoint: PUT /api/v1/admin/campaigns/{id}/reject
    @PutMapping("/api/v1/admin/campaigns/{id}/reject")
    CampaignActionResponse reject(@PathVariable UUID id) {
        return campaignService.reject(id);
    }
}
