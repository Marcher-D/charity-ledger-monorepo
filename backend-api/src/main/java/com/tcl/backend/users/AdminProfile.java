package com.tcl.backend.users;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "admins")
public class AdminProfile {

    @Id
    @Column(name = "account_id")
    private UUID accountId;

    @OneToOne(optional = false)
    @MapsId
    @JoinColumn(name = "account_id")
    private Account account;

    public UUID getAccountId() {
        return accountId;
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
        /*
         * Do not manually copy account.getId() into accountId here.
         *
         * @MapsId means Hibernate derives admins.account_id from account.id when
         * the entity is persisted. Leaving accountId null on a new AdminProfile also
         * lets Spring Data JPA recognize this as a new row and call persist(...)
         * instead of merge(...).
         */
    }
}
