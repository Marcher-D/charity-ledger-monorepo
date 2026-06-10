package com.tcl.backend.campaigns;

import com.tcl.backend.users.AdminProfile;
import com.tcl.backend.users.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// JPA entity for the campaigns table.
// A campaign is created by a user, reviewed by an admin, and later linked to an on-chain address.
@Entity
@Table(name = "campaigns")
public class Campaign {

    // Primary key of the campaign row.
    @Id
    private UUID id;

    // Campaign title shown to users.
    @Column(nullable = false)
    private String title;

    // Long campaign explanation. SQL column is TEXT instead of a short VARCHAR.
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    // Optional image URL for campaign display.
    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    // Fundraising goal.
    @Column(name = "target_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal targetAmount;

    // Confirmed amount raised so far, updated from successful on-chain donation events.
    @Column(name = "current_raised", nullable = false, precision = 19, scale = 2)
    private BigDecimal currentRaised = BigDecimal.ZERO;

    // Store enum as text like PENDING or OPEN, not as an ordinal number.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CampaignStatus status = CampaignStatus.PENDING;

    // On-chain campaign wallet/address, generated when admin approves and blockchain confirms.
    @Column(name = "on_chain_address", unique = true, length = 120)
    private String onChainAddress;

    // Base64 Ed25519 public key of this campaign's blockchain node.
    // Donations on-chain are DONATE transfers to this key.
    @Column(name = "public_key", length = 64)
    private String publicKey;

    // Orchestrator-assigned node index of the campaign's node.
    @Column(name = "node_index")
    private Integer nodeIndex;

    // Many campaigns can be created by the same user.
    // created_by is a foreign key to users.account_id.
    // optional=false means this relationship is required: a campaign must have a creator.
    @ManyToOne(optional = false)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    // Many campaigns can be approved by the same admin.
    // Nullable because pending/rejected campaigns may not have an approving admin yet.
    // There is no optional=false here, so approvedBy can be null.
    @ManyToOne
    @JoinColumn(name = "approved_by")
    private AdminProfile approvedBy;

    // Time the campaign request was created.
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // Runs before first database insert to fill generated fields if service code did not.
    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public void setCoverImageUrl(String coverImageUrl) {
        this.coverImageUrl = coverImageUrl;
    }

    public BigDecimal getTargetAmount() {
        return targetAmount;
    }

    public void setTargetAmount(BigDecimal targetAmount) {
        this.targetAmount = targetAmount;
    }

    public BigDecimal getCurrentRaised() {
        return currentRaised;
    }

    public void setCurrentRaised(BigDecimal currentRaised) {
        this.currentRaised = currentRaised;
    }

    public CampaignStatus getStatus() {
        return status;
    }

    public void setStatus(CampaignStatus status) {
        this.status = status;
    }

    public String getOnChainAddress() {
        return onChainAddress;
    }

    public void setOnChainAddress(String onChainAddress) {
        this.onChainAddress = onChainAddress;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public Integer getNodeIndex() {
        return nodeIndex;
    }

    public void setNodeIndex(Integer nodeIndex) {
        this.nodeIndex = nodeIndex;
    }

    public AppUser getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(AppUser createdBy) {
        this.createdBy = createdBy;
    }

    public AdminProfile getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(AdminProfile approvedBy) {
        this.approvedBy = approvedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
