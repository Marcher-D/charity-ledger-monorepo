package com.tcl.backend.campaigns;

import com.tcl.backend.common.ApiException;
import com.tcl.backend.common.MoneyFormatter;
import com.tcl.backend.common.PageResponse;
import com.tcl.backend.messaging.MempoolPublisher;
import com.tcl.backend.messaging.WalletService;
import com.tcl.backend.security.AuthenticatedAccount;
import com.tcl.backend.transactions.TransactionCache;
import com.tcl.backend.transactions.TransactionCacheRepository;
import com.tcl.backend.transactions.TransactionStatus;
import com.tcl.backend.transactions.TransactionType;
import com.tcl.backend.users.AdminProfile;
import com.tcl.backend.users.AdminRepository;
import com.tcl.backend.users.AppUser;
import com.tcl.backend.users.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Service layer for campaign business logic.
// Controllers call this class; this class coordinates repositories, wallet generation,
// money formatting, and mempool publishing.
@Service
public class CampaignService {

    // Database access for campaigns table.
    private final CampaignRepository campaignRepository;
    // Used to find the logged-in user's AppUser profile.
    private final UserRepository userRepository;
    // Used to verify that an approving caller has an admin profile.
    private final AdminRepository adminRepository;
    // Database access for cached outbound blockchain transactions.
    private final TransactionCacheRepository transactionRepository;
    // Requests campaign wallet/on-chain addresses from the internal on-chain wallet service.
    private final WalletService walletService;
    // Publishes CREATE transactions to RabbitMQ mempool queue.
    private final MempoolPublisher mempoolPublisher;
    // Normalizes money values to the project's fixed decimal format.
    private final MoneyFormatter moneyFormatter;

    // Constructor injection: Spring provides all required beans.
    public CampaignService(
            CampaignRepository campaignRepository,
            UserRepository userRepository,
            AdminRepository adminRepository,
            TransactionCacheRepository transactionRepository,
            WalletService walletService,
            MempoolPublisher mempoolPublisher,
            MoneyFormatter moneyFormatter
    ) {
        this.campaignRepository = campaignRepository;
        this.userRepository = userRepository;
        this.adminRepository = adminRepository;
        this.transactionRepository = transactionRepository;
        this.walletService = walletService;
        this.mempoolPublisher = mempoolPublisher;
        this.moneyFormatter = moneyFormatter;
    }

    // Public campaign listing/search endpoint.
    // readOnly=true because this method only reads database state.
    // A read-only transaction tells Spring/Hibernate this method should not write changes.
    @Transactional(readOnly = true)
    public PageResponse<CampaignResponse> search(String status, String q, String sort, int page, int limit) {
        // Convert status string from query parameter into CampaignStatus enum.
        // Null means no status filter.
        CampaignStatus campaignStatus = parseStatus(status);
        // Spring Data page numbers start at 0, but API page numbers start at 1.
        // limit is clamped between 1 and 100 so clients cannot request huge pages.
        // sort is the frontend query text, for example "title,asc".
        // parseSort(sort) converts that text into a Spring Sort object.
        //
        // PageRequest.of(pageNumber, pageSize, sort) means:
        // pageNumber: which page to return. Spring starts counting pages at 0.
        // pageSize: how many rows/items per page.
        // sort: order of rows before paging.
        //
        // PageRequest does not contain campaigns. It only describes what slice of
        // the campaign list we want. Spring Data uses it like SQL LIMIT/OFFSET/ORDER BY.
        PageRequest pageRequest = PageRequest.of(Math.max(page, 1) - 1, Math.min(Math.max(limit, 1), 100), parseSort(sort));
        // Blank search text should behave like no search.
        // Use empty string instead of null so PostgreSQL treats :q as text in lower(...).
        String normalizedQuery = q == null || q.isBlank() ? "" : q.trim();
        // Repository returns Page<Campaign>. map(CampaignResponse::from) converts each Campaign to DTO.
        // PageResponse.from converts Spring Page into our clean API pagination response.
        return PageResponse.from(campaignRepository.search(campaignStatus, normalizedQuery, pageRequest).map(CampaignResponse::from));
    }

    // Public campaign detail endpoint.
    @Transactional(readOnly = true)
    public CampaignResponse get(UUID id) {
        return CampaignResponse.from(findCampaign(id));
    }

    // Public donor/transaction summary endpoint for one campaign.
    @Transactional(readOnly = true)
    public List<CampaignDonorResponse> donors(UUID id) {
        // Check campaign exists first so unknown campaign id returns a clear 404.
        if (!campaignRepository.existsById(id)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Campaign not found");
        }
        // Repository returns lightweight DonorSummary projections, then we convert them to response DTOs.
        // stream() lets Java process the list item by item.
        // map(CampaignDonorResponse::from) converts each DonorSummary into CampaignDonorResponse.
        // toList() collects the converted responses back into a List.
        return transactionRepository.findDonorsByCampaignId(id).stream()
                .map(CampaignDonorResponse::from)
                .toList();
    }

    // User creates a campaign request. It starts as PENDING and is not sent on-chain yet.
    // A normal transaction is needed because this method writes a campaign row.
    @Transactional
    public CampaignResponse create(CreateCampaignRequest request, AuthenticatedAccount principal) {
        // A campaign must be created by a real AppUser profile.
        AppUser user = findUser(principal);
        Campaign campaign = new Campaign();
        campaign.setTitle(request.title().trim());
        campaign.setDescription(request.description().trim());
        campaign.setCoverImageUrl(request.coverImageUrl());
        // Normalize BigDecimal before saving so money scale is consistent.
        campaign.setTargetAmount(moneyFormatter.normalize(request.targetAmount()));
        campaign.setCurrentRaised(BigDecimal.ZERO);
        campaign.setStatus(CampaignStatus.PENDING);
        // Database column campaigns.created_by stores only a UUID.
        // In Java/JPA we pass the AppUser object; Hibernate stores user.accountId as the FK value.
        campaign.setCreatedBy(user);
        return CampaignResponse.from(campaignRepository.save(campaign));
    }

    // Admin approves a campaign request and creates the outbound on-chain CREATE transaction.
    // A normal transaction is needed because several DB writes should succeed/fail together:
    // campaign update, transaction_cache insert, and txHash update.
    @Transactional
    public CampaignActionResponse approve(UUID id, AuthenticatedAccount principal) {
        Campaign campaign = findCampaign(id);
        // Only fresh pending requests can be approved.
        if (campaign.getStatus() != CampaignStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "Only PENDING campaigns can be approved");
        }

        // Caller must have an AdminProfile row, not just any authenticated account.
        AdminProfile admin = findAdmin(principal);
        // PROCESSING means approved off-chain, waiting for blockchain CREATE confirmation.
        campaign.setStatus(CampaignStatus.PROCESSING);
        campaign.setApprovedBy(admin);
        // Generate/store the campaign on-chain address before publishing CREATE.
        campaign.setOnChainAddress(walletService.generateCampaignWalletAddress(campaign.getId()));
        campaignRepository.save(campaign);

        // Cache the outbound CREATE transaction as PENDING before publishing to RabbitMQ.
        TransactionCache transaction = new TransactionCache();
        transaction.setType(TransactionType.CREATE);
        transaction.setUser(campaign.getCreatedBy());
        transaction.setCampaign(campaign);
        transaction.setAmount(BigDecimal.ZERO);
        transaction.setNote("CREATE_CAMPAIGN");
        transaction.setStatus(TransactionStatus.PENDING);
        // saveAndFlush forces Hibernate to insert now so transaction.id exists before publishing.
        transactionRepository.saveAndFlush(transaction);
        // Publisher signs/builds JSON, sets txHash on transaction, and sends it to mempool_queue.
        mempoolPublisher.publish(transaction, campaign.getCreatedBy().getWalletAddress(), campaign.getOnChainAddress());
        // Save again because publish(...) sets txHash on the transaction object.
        transactionRepository.save(transaction);

        return new CampaignActionResponse("Campaign approval is processing on-chain", campaign.getStatus(), transaction.getId());
    }

    // Admin rejects a campaign request.
    // A normal transaction is needed because this method updates campaign status.
    @Transactional
    public CampaignActionResponse reject(UUID id) {
        Campaign campaign = findCampaign(id);
        // Completed/open campaigns cannot be rejected with this API.
        if (campaign.getStatus() != CampaignStatus.PENDING && campaign.getStatus() != CampaignStatus.PROCESSING) {
            throw new ApiException(HttpStatus.CONFLICT, "Campaign cannot be rejected from current status");
        }
        campaign.setStatus(CampaignStatus.REJECTED);
        campaignRepository.save(campaign);
        return new CampaignActionResponse("Campaign rejected", campaign.getStatus(), null);
    }

    // Shared lookup helper that returns 404 when campaign id is unknown.
    private Campaign findCampaign(UUID id) {
        return campaignRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Campaign not found"));
    }

    // Shared helper for endpoints requiring a normal user profile.
    private AppUser findUser(AuthenticatedAccount principal) {
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "User profile is required"));
    }

    // Shared helper for endpoints requiring an admin profile.
    private AdminProfile findAdmin(AuthenticatedAccount principal) {
        return adminRepository.findById(principal.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Admin profile is required"));
    }

    // Convert optional query string into CampaignStatus enum.
    // Invalid status text becomes a 400 Bad Request.
    private CampaignStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return CampaignStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid campaign status");
        }
    }

    // Convert sort query text into a Spring Sort object.
    // Supported examples: createdAt,desc | title,asc | targetAmount,desc | currentRaised,asc.
    private Sort parseSort(String sort) {
        // If the client does not send sort, use newest campaigns first.
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }

        // Split at the first comma only.
        // Example: "title,asc" becomes ["title", "asc"].
        // Example: "title" becomes ["title"].
        String[] parts = sort.split(",", 2);

        // Only allow known entity properties so clients cannot sort by arbitrary field names.
        // switch expression returns one value into property.
        // parts[0].trim() is the requested field name without surrounding spaces.
        String property = switch (parts[0].trim()) {
            case "title" -> "title";
            case "targetAmount" -> "targetAmount";
            case "currentRaised" -> "currentRaised";
            // Unknown sort fields fall back to createdAt instead of being passed to JPA.
            default -> "createdAt";
        };

        // Ternary syntax:
        // condition ? valueIfTrue : valueIfFalse
        //
        // If the client sent a second part and it equals "asc", use ascending order.
        // Otherwise use descending order.
        Sort.Direction direction = parts.length == 2 && "asc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        // Build the Spring Sort object used by PageRequest.
        return Sort.by(direction, property);
    }
}
