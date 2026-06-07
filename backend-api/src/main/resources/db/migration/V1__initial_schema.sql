-- Initial PostgreSQL schema for the Spring Boot off-chain backend.
-- Flyway runs this file once when the database is empty.
-- Naming note: table/column names use snake_case because that is standard in SQL.

-- Login identity table.
-- Both normal users and admins start as an account.
CREATE TABLE accounts (
    -- Main unique row id. UUID is the data type; PRIMARY KEY makes it unique and not null.
    id UUID PRIMARY KEY,

    -- Display/user-facing name.
    username VARCHAR(100) NOT NULL,

    -- BCrypt password hash. Never store raw passwords.
    password_hash VARCHAR(255) NOT NULL,

    -- Login email. UNIQUE prevents two accounts from using the same email.
    email VARCHAR(255) NOT NULL UNIQUE,

    -- Role used by Spring Security for route protection.
    role VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN', 'USER')),

    -- Optional profile image URL.
    avatar_url VARCHAR(500),

    -- Account creation time. PostgreSQL fills this automatically if Java does not.
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Normal user profile table.
-- This table extends accounts with wallet/balance fields.
CREATE TABLE users (
    -- Also the primary key, and also a foreign key to accounts.id.
    -- ON DELETE CASCADE means deleting the account deletes the user profile too.
    account_id UUID PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,

    -- Wallet address used by the on-chain network.
    wallet_address VARCHAR(120) NOT NULL UNIQUE,

    -- Spendable off-chain cached balance.
    available_balance NUMERIC(19, 2) NOT NULL DEFAULT 0.00,

    -- Money locked while a transaction waits for Event Bus confirmation.
    -- The update logs call this the escrow/pending state used to prevent double spending.
    pending_balance NUMERIC(19, 2) NOT NULL DEFAULT 0.00,

    -- Amount currently locked for validator/staking participation.
    staked_balance NUMERIC(19, 2) NOT NULL DEFAULT 0.00,

    -- Whether this user is currently active as a validator candidate.
    is_active_validator BOOLEAN NOT NULL DEFAULT FALSE
);

-- Admin profile table.
-- Admins are accounts with admin privileges, but they do not need wallet/balance fields here.
CREATE TABLE admins (
    -- Same id as accounts.id. One admin profile belongs to one account.
    account_id UUID PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE
);

-- Charity campaign table.
-- A user creates a campaign request; an admin can approve or reject it.
CREATE TABLE campaigns (
    -- Unique campaign id.
    id UUID PRIMARY KEY,

    -- Campaign title shown in the UI.
    title VARCHAR(255) NOT NULL,

    -- Long campaign description.
    description TEXT NOT NULL,

    -- Optional image URL for the campaign card/detail page.
    cover_image_url VARCHAR(500),

    -- Fundraising goal.
    target_amount NUMERIC(19, 2) NOT NULL,

    -- Cached confirmed amount raised so far.
    current_raised NUMERIC(19, 2) NOT NULL DEFAULT 0.00,

    -- Lifecycle:
    -- PENDING: waiting for admin review.
    -- PROCESSING: approved off-chain, waiting for on-chain CREATE confirmation.
    -- OPEN: on-chain campaign created and donation is allowed.
    -- COMPLETED: target amount reached.
    -- REJECTED: admin rejected the campaign.
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'REJECTED', 'PROCESSING', 'OPEN', 'COMPLETED')),

    -- On-chain wallet/address for this campaign. UNIQUE because one address should map to one campaign.
    on_chain_address VARCHAR(120) UNIQUE,

    -- User who created the campaign. Not unique: one user can create many campaigns.
    created_by UUID NOT NULL REFERENCES users(account_id),

    -- Admin who approved the campaign. Nullable while campaign is still pending or rejected before approval.
    approved_by UUID REFERENCES admins(account_id),

    -- Campaign creation time.
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Off-chain transaction cache table.
-- Every outbound request to the blockchain is stored here first as PENDING.
-- Later, RabbitMQ event-bus messages update the row to SUCCESS or FAILED.
CREATE TABLE transactions_cache (
    -- Backend transaction id. This is sent as txId in outbound JSON.
    id UUID PRIMARY KEY,

    -- Hash returned by the signer/publisher path. Nullable before publishing/signing completes.
    tx_hash VARCHAR(128),

    -- Business type of transaction.
    -- The update logs add REWARD, SLASH, DEPOSIT, and WITHDRAW.
    -- CREATE and VALIDATOR_TOGGLE are kept because the original backend API still has
    -- campaign approval and validator toggle flows.
    type VARCHAR(30) NOT NULL CHECK (type IN (
        'DONATE',
        'REFUND',
        'CREATE',
        'STAKE',
        'UNSTAKE',
        'VALIDATOR_TOGGLE',
        'REWARD',
        'SLASH',
        'DEPOSIT',
        'WITHDRAW'
    )),

    -- User who initiated the transaction.
    user_id UUID NOT NULL REFERENCES users(account_id),

    -- Optional campaign receiver. Null for stake, unstake, and validator toggle.
    campaign_id UUID REFERENCES campaigns(id),

    -- Transaction amount. CREATE and VALIDATOR_TOGGLE can use 0.00.
    -- For user-spend transactions, this amount is first moved into users.pending_balance.
    amount NUMERIC(19, 2) NOT NULL DEFAULT 0.00,

    -- Small extra context:
    -- DONATE uses it as donor note.
    -- CREATE/STAKE/UNSTAKE use it as an action label.
    -- VALIDATOR_TOGGLE stores OPT_IN or OPT_OUT.
    note VARCHAR(500),

    -- Blockchain confirmation status from the backend point of view.
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED')),

    -- Time this cached transaction was created.
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Speeds up public campaign listing by status and newest-first order.
CREATE INDEX idx_campaigns_status_created_at ON campaigns(status, created_at DESC);

-- Speeds up "my transactions" queries for a user.
CREATE INDEX idx_transactions_user_created_at ON transactions_cache(user_id, created_at DESC);

-- Speeds up donor/history queries for one campaign.
CREATE INDEX idx_transactions_campaign_created_at ON transactions_cache(campaign_id, created_at DESC);

-- Speeds up lookup when an on-chain event references txHash instead of txId.
CREATE INDEX idx_transactions_hash ON transactions_cache(tx_hash);
