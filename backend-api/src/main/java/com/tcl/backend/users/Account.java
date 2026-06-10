package com.tcl.backend.users;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

// @Entity tells JPA/Hibernate that this Java class is stored in a database table.
@Entity
// This class maps to the SQL table named accounts in V1__initial_schema.sql.
@Table(name = "accounts")
public class Account {

    // Primary key column. UUID is used instead of auto-increment integers.
    @Id
    private UUID id;

    // User-facing display name. nullable=false matches NOT NULL in the SQL schema.
    @Column(nullable = false, length = 100)
    private String username;

    // Stored password hash, not the raw password.
    // The column name is password_hash because SQL uses snake_case.
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    // Login email. unique=true means two accounts cannot use the same email.
    @Column(nullable = false, unique = true)
    private String email;

    // Store the enum as text like USER or ADMIN, not as number 0 or 1.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountRole role;

    // Optional profile image URL. No nullable=false, so this can be null.
    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    // Time the account was created. Java uses Instant for an exact timestamp.
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // Runs automatically right before JPA inserts this object into the database.
    // This protects us if service code forgets to set id or createdAt.
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

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public AccountRole getRole() {
        return role;
    }

    public void setRole(AccountRole role) {
        this.role = role;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
