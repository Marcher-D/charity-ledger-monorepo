package com.tcl.backend.users;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

// @Entity tells JPA/Hibernate that this class is stored in a database table.
@Entity
// This class maps to the users table in V1__initial_schema.sql.
@Table(name = "users")
public class AppUser {

    // Primary key of the users table.
    // This is also a foreign key to accounts.id.
    @Id
    @Column(name = "account_id")
    private UUID accountId;

    // One user profile belongs to exactly one Account.
    // optional=false means AppUser cannot exist without an Account.
    @OneToOne(optional = false)
    // @MapsId means this entity uses account.id as its own primary key too.
    // So users.account_id and accounts.id have the same value.
    @MapsId
    // Join users.account_id to accounts.id.
    @JoinColumn(name = "account_id")
    private Account account;

    // Public blockchain wallet address for this user.
    // unique=true means two users cannot share one wallet address.
    @Column(name = "wallet_address", nullable = false, unique = true, length = 120)
    private String walletAddress;

    // Base64 Ed25519 public key of this user's blockchain node.
    // Used as senderPubKey/receiverPubKey in on-chain transactions.
    @Column(name = "public_key", length = 64)
    private String publicKey;

    // Orchestrator-assigned node index. Selects the node's signing service
    // (http://node-<index>:7100) and its P2P port (9000+index).
    @Column(name = "node_index")
    private Integer nodeIndex;

    // Spendable off-chain cached balance.
    @Column(name = "available_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal availableBalance = BigDecimal.ZERO;

    // Escrow/locked amount while a transaction waits for Event Bus confirmation.
    @Column(name = "pending_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal pendingBalance = BigDecimal.ZERO;

    // Amount currently staked for validator participation.
    @Column(name = "staked_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal stakedBalance = BigDecimal.ZERO;

    // Whether this user is currently active as a validator.
    @Column(name = "is_active_validator", nullable = false)
    private boolean activeValidator;

    public UUID getAccountId() {
        return accountId;
    }

    public Account getAccount() {
        return account;
    }

    // Setting the Account is enough because @MapsId derives users.account_id from account.id.
    // We intentionally leave accountId null on new AppUser objects so Spring Data treats
    // them as new rows and uses persist(...) instead of merge(...).
    public void setAccount(Account account) {
        this.account = account;
    }

    public String getWalletAddress() {
        return walletAddress;
    }

    public void setWalletAddress(String walletAddress) {
        this.walletAddress = walletAddress;
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

    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }

    public void setAvailableBalance(BigDecimal availableBalance) {
        this.availableBalance = availableBalance;
    }

    public BigDecimal getPendingBalance() {
        return pendingBalance;
    }

    public void setPendingBalance(BigDecimal pendingBalance) {
        this.pendingBalance = pendingBalance;
    }

    public BigDecimal getStakedBalance() {
        return stakedBalance;
    }

    public void setStakedBalance(BigDecimal stakedBalance) {
        this.stakedBalance = stakedBalance;
    }

    public boolean isActiveValidator() {
        return activeValidator;
    }

    public void setActiveValidator(boolean activeValidator) {
        this.activeValidator = activeValidator;
    }
}
