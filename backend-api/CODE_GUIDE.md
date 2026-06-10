# Backend API Code Guide

This guide explains how to read and understand the Spring Boot backend for Khoi's role in Transparent Charity Ledger.

Use this file as your study path. Do not start by opening random files. Read in the order below, because each layer depends on the previous one.

## 1. Big Picture

This backend is the off-chain system. It does not implement blockchain consensus, P2P networking, or SQLite block storage.

Its job is to:

- Receive HTTP requests from the frontend.
- Authenticate users and admins with JWT.
- Store off-chain data in PostgreSQL.
- Cache outgoing blockchain transactions in `transactions_cache`.
- Lock money in `users.pending_balance` while a transaction is waiting for blockchain confirmation.
- Publish transaction messages through RabbitMQ `mempool_exchange` with routing key `mempool_queue`.
- Consume on-chain result events from RabbitMQ `event_bus_queue`.
- Update PostgreSQL after the blockchain confirms success or failure.

Main architecture:

```text
Frontend
  -> Controller
  -> Service
  -> Repository
  -> PostgreSQL

Service
  -> MempoolPublisher
  -> RabbitMQ mempool_queue
  -> Blockchain nodes

Blockchain nodes
  -> RabbitMQ event_bus_queue
  -> OnChainEventListener
  -> OnChainEventService
  -> PostgreSQL updates
```

### Project Lead Update Logs Now Implemented

The update logs changed four important backend rules:

- `users` now has `pending_balance`.
- Outbound transaction JSON now has `nonce_order`.
- Event Bus messages now use `eventId`, `eventType`, `timestamp`, and `payload`.
- Event Bus event types are `BLOCK_SUCCESS`, `BLOCK_FAIL`, `TX_FAIL`, and `TX_EXPIRED`.

The most important behavior change is money locking:

```text
User starts transaction
-> backend checks available/staked balance
-> backend moves money into pending_balance
-> backend publishes to mempool_queue
-> Event Bus later confirms success/failure/expiry
-> backend settles or refunds pending_balance
```

## 2. Start Here

Read these files first:

```text
pom.xml
src/main/resources/application.yml
src/main/java/com/tcl/backend/BackendApiApplication.java
src/main/resources/db/migration/V1__initial_schema.sql
```

### `pom.xml`

This defines the project and dependencies.

Important dependencies:

- `spring-boot-starter-web`: REST API controllers.
- `spring-boot-starter-security`: login protection and role checks.
- `spring-boot-starter-data-jpa`: database access with JPA/Hibernate.
- `spring-boot-starter-amqp`: RabbitMQ integration.
- `flyway-core`: database migration.
- `postgresql`: PostgreSQL driver.
- `jjwt-*`: JWT token creation and verification.
- `spring-boot-starter-test`: testing.

Important setting:

```xml
<java.version>21</java.version>
```

The project targets Java 21.

### `application.yml`

This is the runtime configuration.

Important sections:

- `spring.datasource`: PostgreSQL connection.
- `spring.jpa.hibernate.ddl-auto: validate`: Hibernate must match the Flyway schema.
- `spring.rabbitmq`: RabbitMQ connection.
- `app.jwt`: JWT secret and expiry.
- `app.rabbitmq`: queue names.
- `app.bootstrap-admin`: default admin account.

Default queues:

```text
mempool_queue
event_bus_queue
mempool_exchange
mempool_dlq
event_bus_dlq
```

### `BackendApiApplication.java`

This is the Spring Boot entry point.

When you run:

```bash
mvn spring-boot:run
```

Spring starts from this class, scans all packages under `com.tcl.backend`, creates beans, starts controllers, connects to the database, and starts RabbitMQ listeners.

### `V1__initial_schema.sql`

This is the real database schema.

Tables:

- `accounts`: login identity, email, password hash, role.
- `users`: user wallet, available balance, pending balance, staked balance, validator state.
- `admins`: admin profile linked to an account.
- `campaigns`: charity campaigns.
- `transactions_cache`: off-chain cache of outbound transactions and their status.

Read this before reading entity classes. The entity classes are Java versions of these tables.

## 3. Package Map

The source code is under:

```text
src/main/java/com/tcl/backend
```

Packages:

```text
auth          Register/login APIs and JWT issuing
users         Account, user profile, admin profile, repositories
security      Spring Security and JWT filter
campaigns     Campaign APIs, campaign business logic, campaign database access
transactions  Donate/stake/unstake/validator APIs and transaction cache
messaging     RabbitMQ publisher/listener and wallet/signing adapters
common        Shared errors, pagination, money formatting
config        App configuration and default admin bootstrap
```

Read packages in this order:

```text
common
users
security
auth
campaigns
transactions
messaging
config
tests
```

## 4. Common Package

Folder:

```text
src/main/java/com/tcl/backend/common
```

Files:

```text
ApiException.java
ErrorResponse.java
GlobalExceptionHandler.java
MoneyFormatter.java
PageResponse.java
```

### `ApiException`

Custom exception for expected API errors.

Example:

```java
throw new ApiException(HttpStatus.NOT_FOUND, "Campaign not found");
```

This lets services return correct HTTP status codes without manually building responses.

### `ErrorResponse`

Standard JSON error shape returned to frontend.

It contains:

- timestamp
- status code
- error name
- message
- validation errors

### `GlobalExceptionHandler`

Catches exceptions from controllers/services and converts them into JSON errors.

It handles:

- `ApiException`
- validation errors
- access denied errors
- unexpected server errors

This keeps controllers clean.

### `MoneyFormatter`

Important for blockchain consistency.

It normalizes money to two decimal places:

```text
50 -> 50.00
50.1 -> 50.10
```

This matters because blockchain hashes can change if one service writes `50.0` and another writes `50.00`.

### `PageResponse`

Standard response for paginated endpoints like campaign listing.

It wraps:

- items
- page
- limit
- totalItems
- totalPages

## 5. Users Package

Folder:

```text
src/main/java/com/tcl/backend/users
```

Read these first:

```text
AccountRole.java
Account.java
AppUser.java
AdminProfile.java
```

Then:

```text
AccountRepository.java
UserRepository.java
AdminRepository.java
UserService.java
UserController.java
UserProfileResponse.java
```

### `AccountRole`

Enum:

```text
ADMIN
USER
```

Used by Spring Security for route protection.

### `Account`

Maps to `accounts`.

This is the login identity:

- id
- username
- passwordHash
- email
- role
- avatarUrl
- createdAt

Important:

```java
@PrePersist
```

Before saving a new account, it generates:

- UUID id
- createdAt timestamp

### `AppUser`

Maps to `users`.

This is extra user data linked one-to-one with `Account`.

Fields:

- walletAddress
- availableBalance
- stakedBalance
- activeValidator

The primary key is `account_id`, same as the account id.

### `AdminProfile`

Maps to `admins`.

This is extra admin data linked one-to-one with `Account`.

Admin accounts do not have wallet balances in this model.

### Repositories

Repositories are database access interfaces.

Examples:

```java
Optional<Account> findByEmail(String email);
boolean existsByEmail(String email);
Optional<AppUser> findByWalletAddress(String walletAddress);
```

Spring Data JPA automatically implements these from method names.

### `UserService`

Contains user profile logic.

Main method:

```java
me(AuthenticatedAccount principal)
```

It returns profile data for the logged-in user.

If the account is an admin, there may be no `AppUser` row, so it returns profile info with null wallet and zero balances.

### `UserController`

Exposes:

```text
GET /api/v1/users/me
```

It uses:

```java
@AuthenticationPrincipal AuthenticatedAccount principal
```

That means Spring Security injects the logged-in account.

## 6. Security Package

Folder:

```text
src/main/java/com/tcl/backend/security
```

Read in this order:

```text
AuthenticatedAccount.java
AccountDetailsService.java
JwtService.java
JwtAuthenticationFilter.java
SecurityConfig.java
```

### `AuthenticatedAccount`

Wraps `Account` so Spring Security can understand it.

Important method:

```java
getAuthorities()
```

It returns:

```text
ROLE_USER
ROLE_ADMIN
```

Spring Security uses these roles to protect APIs.

### `AccountDetailsService`

Loads an account by email during login or token authentication.

Spring Security calls:

```java
loadUserByUsername(String email)
```

Even though the method says "username", this backend uses email.

### `JwtService`

Creates and validates JWT tokens.

Main methods:

- `generateToken(...)`
- `extractSubject(...)`
- `isValid(...)`

The token contains:

- email as subject
- account id
- role
- issued time
- expiration time

### `JwtAuthenticationFilter`

Runs on every HTTP request.

It checks:

```text
Authorization: Bearer <JWT>
```

If the token is valid, it puts the authenticated user into Spring Security context.

If the token is invalid, it returns HTTP 401.

### `SecurityConfig`

Defines route protection.

Public:

```text
/api/v1/auth/**
GET /api/v1/campaigns/**
```

Admin only:

```text
/api/v1/admin/**
```

Everything else requires login.

## 7. Auth Package

Folder:

```text
src/main/java/com/tcl/backend/auth
```

Read in this order:

```text
RegisterRequest.java
LoginRequest.java
AuthResponse.java
AuthService.java
AuthController.java
```

### Request/Response Classes

`RegisterRequest` is input for registration:

- username
- email
- password

`LoginRequest` is input for login:

- email
- password

`AuthResponse` is returned after register/login:

- accessToken
- tokenType
- expiresIn

### `AuthService.register`

Flow:

```text
Validate email is not used
Create Account with role USER
Hash password with BCrypt
Generate wallet address
Create AppUser
Return JWT token
```

Important dependency:

```java
WalletService
```

The current implementation calls the internal on-chain wallet API. The backend receives a wallet address, but never receives a private key.

### `AuthService.login`

Flow:

```text
Normalize email
Ask AuthenticationManager to verify password
Load Account
Return JWT token
```

### `AuthController`

Exposes:

```text
POST /api/v1/auth/register
POST /api/v1/auth/login
```

Controllers should stay thin. They receive HTTP data and call services.

## 8. Campaigns Package

Folder:

```text
src/main/java/com/tcl/backend/campaigns
```

Read in this order:

```text
CampaignStatus.java
Campaign.java
CreateCampaignRequest.java
CampaignResponse.java
CampaignDonorResponse.java
CampaignActionResponse.java
CampaignRepository.java
CampaignService.java
CampaignController.java
```

### `CampaignStatus`

Enum:

```text
PENDING
REJECTED
PROCESSING
OPEN
COMPLETED
```

Meaning:

- `PENDING`: user created campaign, waiting for admin.
- `PROCESSING`: admin approved, waiting for blockchain create confirmation.
- `OPEN`: campaign is live and can receive donations.
- `COMPLETED`: target amount reached.
- `REJECTED`: admin rejected it.

### `Campaign`

Maps to `campaigns`.

Important relationships:

- `createdBy`: links to `AppUser`
- `approvedBy`: links to `AdminProfile`

Important fields:

- targetAmount
- currentRaised
- status
- onChainAddress

### DTO Classes

DTO means Data Transfer Object.

These classes define HTTP input/output shapes:

- `CreateCampaignRequest`: request body for creating a campaign.
- `CampaignResponse`: campaign JSON returned to frontend.
- `CampaignDonorResponse`: donor rows for a campaign.
- `CampaignActionResponse`: result of approve/reject actions.

### `CampaignRepository`

Database access for campaigns.

Important method:

```java
search(CampaignStatus status, String q, Pageable pageable)
```

It supports:

- optional status filtering
- optional text search by title/description
- pagination
- sorting from `Pageable`

### `CampaignService`

This is the main business logic for campaigns.

Important dependencies:

- `CampaignRepository`: save/read campaigns.
- `UserRepository`: load logged-in user profile.
- `AdminRepository`: confirm logged-in admin profile.
- `TransactionCacheRepository`: create `CREATE` transaction cache.
- `WalletService`: generate campaign on-chain address.
- `MempoolPublisher`: publish transaction to RabbitMQ.
- `MoneyFormatter`: normalize target amount.

Main methods:

```java
search(...)
get(...)
donors(...)
create(...)
approve(...)
reject(...)
```

#### `search`

Flow:

```text
Parse campaign status string
Build PageRequest
Normalize search query
Call campaignRepository.search
Convert Campaign entities to CampaignResponse
Wrap result in PageResponse
```

#### `create`

Flow:

```text
Get logged-in AppUser
Create Campaign
Set status PENDING
Save to PostgreSQL
Return CampaignResponse
```

No blockchain transaction is published here. User creation request is off-chain until admin approval.

#### `approve`

This is one of the most important backend flows.

Flow:

```text
Find campaign
Require status PENDING
Find admin profile from logged-in account
Set campaign status PROCESSING
Generate campaign on-chain address
Save campaign
Create transactions_cache row with type CREATE
Save and flush transaction to get tx id
Publish transaction to mempool_queue
Save txHash returned by signer/publisher
Return action response
```

Why `saveAndFlush` before publishing?

The transaction needs an id before signing/publishing. The id becomes part of the outbound transaction message.

#### `reject`

Flow:

```text
Find campaign
Allow only PENDING or PROCESSING
Set status REJECTED
Save campaign
```

### `CampaignController`

Exposes:

```text
GET  /api/v1/campaigns
GET  /api/v1/campaigns/{id}
GET  /api/v1/campaigns/{id}/donors
POST /api/v1/campaigns
PUT  /api/v1/admin/campaigns/{id}/approve
PUT  /api/v1/admin/campaigns/{id}/reject
```

Remember:

- `GET /campaigns/**` is public.
- `POST /campaigns` requires login.
- `/admin/**` requires admin role.

## 9. Transactions Package

Folder:

```text
src/main/java/com/tcl/backend/transactions
```

Read in this order:

```text
TransactionType.java
TransactionStatus.java
TransactionCache.java
DonateRequest.java
AmountRequest.java
ValidatorToggleRequest.java
TransactionResponse.java
TransactionActionResponse.java
DonorSummary.java
TransactionCacheRepository.java
TransactionService.java
TransactionController.java
```

### `TransactionType`

Enum:

```text
DONATE
REFUND
CREATE
STAKE
UNSTAKE
VALIDATOR_TOGGLE
REWARD
SLASH
DEPOSIT
WITHDRAW
```

The update logs explicitly added:

```text
REWARD
SLASH
DEPOSIT
WITHDRAW
```

`CREATE` and `VALIDATOR_TOGGLE` are still kept because this backend already has campaign approval and validator toggle APIs.

### `TransactionStatus`

Enum:

```text
PENDING
SUCCESS
FAILED
```

### `TransactionCache`

Maps to `transactions_cache`.

This is not the blockchain block transaction table. It is the off-chain backend's cache of requests sent to the blockchain.

Important fields:

- id: tx id used by backend.
- txHash: hash/signature result from signer.
- type: donate/stake/create/etc.
- user: sender.
- campaign: optional receiver campaign.
- amount.
- note.
- status.

### Request Classes

- `DonateRequest`: campaign id, amount, note.
- `AmountRequest`: amount only, used for stake/unstake.
- `ValidatorToggleRequest`: `OPT_IN` or `OPT_OUT`.

### Response Classes

- `TransactionResponse`: full transaction view.
- `TransactionActionResponse`: response when a transaction is accepted and pending.

### `DonorSummary`

Projection interface for campaign donor listing.

Spring Data JPA can return only selected fields instead of full entities.

Used by:

```java
findDonorsByCampaignId(...)
```

### `TransactionCacheRepository`

Important methods:

```java
findByUserAccountId(...)
findByTxHash(...)
findDonorsByCampaignId(...)
```

### `TransactionService`

This is the main business logic for donate/stake/unstake/validator toggle.

Important constants:

```java
STAKE_POOL_ADDRESS
VALIDATOR_REGISTRY_ADDRESS
```

These are placeholder target addresses until the on-chain team provides real addresses.

Main methods:

```java
myTransactions(...)
donate(...)
stake(...)
unstake(...)
toggleValidator(...)
```

#### `donate`

Flow:

```text
Load logged-in user
Find campaign
Require campaign status OPEN
Normalize amount
Check available balance
Move amount from available_balance to pending_balance
Create PENDING transaction cache row
Publish transaction to mempool_queue
Save txHash
Return PENDING response
```

Important:

The backend does not treat the money as finally spent yet. It locks the money in `pending_balance` first. If Event Bus later sends success, pending money is settled. If Event Bus sends fail or expired, pending money is refunded.

#### `stake`

Flow:

```text
Load user
Normalize amount
Check available balance
Move amount from available_balance to pending_balance
Create PENDING STAKE transaction
Publish to STAKE_POOL target
Return PENDING response
```

#### `unstake`

Flow:

```text
Load user
Reject if active validator
Check staked balance
Move amount from staked_balance to pending_balance
Create PENDING UNSTAKE transaction
Publish to STAKE_POOL target
Return PENDING response
```

#### `toggleValidator`

Flow:

```text
Load user
Require action OPT_IN or OPT_OUT
If OPT_IN, require positive staked balance
Create PENDING VALIDATOR_TOGGLE transaction
Publish to VALIDATOR_REGISTRY target
Return PENDING response
```

### `TransactionController`

Exposes:

```text
POST /api/v1/transactions/donate
POST /api/v1/transactions/stake
POST /api/v1/transactions/unstake
POST /api/v1/transactions/validator/toggle
GET  /api/v1/transactions/me
```

All require login.

## 10. Messaging Package

Folder:

```text
src/main/java/com/tcl/backend/messaging
```

Read in this order:

```text
RabbitQueueProperties.java
RabbitConfig.java
WalletService.java
OnChainWalletService.java
OnChainProperties.java
TransactionSigner.java
SignedTransaction.java
OnChainTransactionSigner.java
NonceService.java
OutboundTransactionMessage.java
MempoolPublisher.java
RabbitMempoolPublisher.java
EventBusEventType.java
EventBusMessage.java
OnChainEventListener.java
OnChainEventService.java
```

### `RabbitQueueProperties`

Reads queue names from:

```yaml
app:
  rabbitmq:
    mempool-exchange: mempool_exchange
    mempool-queue: mempool_queue
    mempool-dlq: mempool_dlq
    event-bus-queue: event_bus_queue
    event-bus-dlq: event_bus_dlq
```

### `RabbitConfig`

Creates RabbitMQ queues and configures JSON message conversion.

Important:

```java
Jackson2JsonMessageConverter
```

This makes Java objects convert to/from JSON messages.

### `WalletService` and `OnChainWalletService`

`WalletService` is an interface.

It defines:

```java
generateUserWalletAddress(UUID ownerId)
generateCampaignWalletAddress(UUID ownerId)
```

`OnChainWalletService` is the current implementation. It calls:

```text
POST /internal/v1/wallets
```

The request sends `ownerType` (`USER` or `CAMPAIGN`) and `ownerId`. The response gives the backend a wallet address. Private keys stay inside the on-chain service.

### `TransactionSigner` and `OnChainTransactionSigner`

`TransactionSigner` is an interface.

It defines:

```java
sign(TransactionCache transaction, long nonceOrder, String senderAddress, String targetAddress, long timestamp, Map<String, Object> payload)
```

`OnChainTransactionSigner` calls:

```text
POST /internal/v1/transactions/sign
```

The response contains the real `publicKey`, `signature`, and `txHash` that are copied into the outbound RabbitMQ message.

### `NonceService`

This creates the `nonce_order` value required by the update logs.

Important idea:

```text
Every outbound transaction gets a bigger nonce_order than the previous one.
```

The current implementation uses the current millisecond time plus an in-memory counter rule. That means the value keeps increasing during runtime and normally will not go backwards after restart because wall-clock time has moved forward. It is not stored in PostgreSQL, matching the update log instruction that nonce should not be stored in the transaction table.

### `OutboundTransactionMessage`

This is the JSON message sent to RabbitMQ `mempool_queue`.

Fields:

- txId
- nonce_order
- type
- senderAddress
- targetAddress
- amount
- timestamp
- payload
- publicKey
- signature
- txHash

### `MempoolPublisher`

Interface for publishing transactions.

Services depend on this interface, not RabbitMQ directly. That makes code easier to test and easier to replace.

### `RabbitMempoolPublisher`

Real publisher implementation.

Flow:

```text
Allocate nonce_order
Sign transaction
Set transaction txHash
Build OutboundTransactionMessage
rabbitTemplate.convertAndSend(mempool_exchange, mempool_queue, message)
```

Important:

It stores `txHash` back into the `TransactionCache` object.

### `EventBusEventType`

Enum for Event Bus messages:

```text
BLOCK_SUCCESS
BLOCK_FAIL
TX_FAIL
TX_EXPIRED
```

### `EventBusMessage`

This is the JSON message expected from RabbitMQ `event_bus_queue` after the project lead update logs.

Fields:

- eventId: idempotency key.
- eventType: one of the `EventBusEventType` values.
- timestamp: Unix time in milliseconds.
- payload: dynamic object. Its fields depend on eventType.

Payload examples:

```text
BLOCK_SUCCESS payload:
blockIndex, blockHash, successfulTxIds

TX_FAIL payload:
txId, senderAddress, reason

TX_EXPIRED payload:
txId, senderAddress, reason

BLOCK_FAIL payload:
proposerAddress, reason, penaltyAction
```

### `OnChainEventListener`

RabbitMQ listener.

It consumes:

```text
event_bus_queue
```

Then calls:

```java
onChainEventService.apply(message)
```

### `OnChainEventService`

Applies blockchain results to PostgreSQL.

Main method:

```java
apply(EventBusMessage event)
```

Flow:

```text
Ignore duplicate eventId during this app runtime
Read eventType
If BLOCK_SUCCESS, settle every txId in successfulTxIds
If TX_FAIL or TX_EXPIRED, mark that transaction FAILED and refund pending money
If BLOCK_FAIL with SLASH_STAKE, reduce proposer staked balance
```

For `CREATE`:

```text
Campaign PROCESSING -> OPEN
```

For `DONATE`:

```text
Release amount from user pending_balance
Increase campaign currentRaised
If currentRaised >= targetAmount, status -> COMPLETED
```

For `STAKE`:

```text
Release amount from pending_balance
Increase staked_balance
```

For `UNSTAKE`:

```text
Release amount from pending_balance
Increase available_balance
```

For `TX_FAIL` or `TX_EXPIRED`:

```text
DONATE/STAKE/WITHDRAW: pending_balance -> available_balance
UNSTAKE: pending_balance -> staked_balance
```

For `STAKE`:

```text
Subtract available balance
Increase staked balance
```

For `UNSTAKE`:

```text
Increase available balance
Decrease staked balance
```

For `VALIDATOR_TOGGLE`:

```text
Set activeValidator from event, or infer from note OPT_IN/OPT_OUT
```

## 11. Config Package

Folder:

```text
src/main/java/com/tcl/backend/config
```

Files:

```text
AppConfig.java
BootstrapAdminInitializer.java
```

### `AppConfig`

Enables configuration properties for:

```java
RabbitQueueProperties
```

This lets Spring bind `application.yml` values into Java records.

### `BootstrapAdminInitializer`

Runs when the app starts.

If enabled, it creates a default admin account:

```text
email: admin@tcl.local
password: Admin123!
```

It only creates the admin if that email does not already exist.

This is useful for demos because there is no admin registration endpoint.

## 12. Important End-to-End Flows

### Register Flow

Files to read:

```text
AuthController.java
AuthService.java
Account.java
AppUser.java
OnChainWalletService.java
JwtService.java
```

Flow:

```text
POST /api/v1/auth/register
-> AuthController.register
-> AuthService.register
-> Check email
-> Save Account
-> Generate wallet address
-> Save AppUser
-> Generate JWT
-> Return token
```

### Login Flow

Files to read:

```text
AuthController.java
AuthService.java
SecurityConfig.java
AccountDetailsService.java
JwtService.java
```

Flow:

```text
POST /api/v1/auth/login
-> AuthController.login
-> AuthService.login
-> AuthenticationManager checks password
-> AccountDetailsService loads account
-> JwtService generates token
-> Return token
```

### Authenticated Request Flow

Files to read:

```text
SecurityConfig.java
JwtAuthenticationFilter.java
JwtService.java
AuthenticatedAccount.java
```

Flow:

```text
HTTP request with Authorization header
-> JwtAuthenticationFilter
-> JwtService validates token
-> AccountDetailsService loads account
-> AuthenticatedAccount stored in SecurityContext
-> Controller receives @AuthenticationPrincipal
```

### Create Campaign Request Flow

Files to read:

```text
CampaignController.java
CampaignService.java
Campaign.java
CampaignRepository.java
```

Flow:

```text
POST /api/v1/campaigns
-> CampaignController.create
-> CampaignService.create
-> Load logged-in AppUser
-> Save campaign as PENDING
-> Return campaign
```

### Admin Approve Campaign Flow

Files to read:

```text
CampaignController.java
CampaignService.java
TransactionCache.java
RabbitMempoolPublisher.java
OnChainTransactionSigner.java
OutboundTransactionMessage.java
```

Flow:

```text
PUT /api/v1/admin/campaigns/{id}/approve
-> CampaignService.approve
-> Campaign status PENDING -> PROCESSING
-> Generate campaign wallet address
-> Create PENDING CREATE transaction
-> Sign/build outbound transaction message
-> Publish to mempool_queue
-> Return processing response
```

### Donate Flow

Files to read:

```text
TransactionController.java
TransactionService.java
TransactionCache.java
RabbitMempoolPublisher.java
OnChainEventService.java
```

Flow:

```text
POST /api/v1/transactions/donate
-> TransactionService.donate
-> Require campaign OPEN
-> Check available balance
-> Create PENDING DONATE transaction
-> Publish to mempool_queue
-> Wait for blockchain event
```

Then later:

```text
event_bus_queue receives SUCCESS
-> OnChainEventListener.consume
-> OnChainEventService.apply
-> transaction status SUCCESS
-> campaign currentRaised increases
-> user availableBalance decreases
```

### Stake Flow

Files to read:

```text
TransactionController.java
TransactionService.java
OnChainEventService.java
```

Flow:

```text
POST /api/v1/transactions/stake
-> Check available balance
-> Create PENDING STAKE transaction
-> Publish to mempool_queue
-> On SUCCESS, availableBalance decreases and stakedBalance increases
```

### Unstake Flow

Flow:

```text
POST /api/v1/transactions/unstake
-> Reject if active validator
-> Check staked balance
-> Create PENDING UNSTAKE transaction
-> Publish to mempool_queue
-> On SUCCESS, availableBalance increases and stakedBalance decreases
```

### Validator Toggle Flow

Flow:

```text
POST /api/v1/transactions/validator/toggle
-> Validate OPT_IN or OPT_OUT
-> OPT_IN requires staked balance
-> Create PENDING VALIDATOR_TOGGLE transaction
-> Publish to mempool_queue
-> On SUCCESS, activeValidator changes
```

## 13. Controller-Service-Repository Pattern

Most features follow this shape:

```text
Controller: HTTP input/output
Service: business rules
Repository: database access
Entity: database table mapping
DTO/Response: JSON shape
```

Example for campaigns:

```text
CampaignController
-> CampaignService
-> CampaignRepository
-> Campaign entity
-> campaigns table
```

Example for transactions:

```text
TransactionController
-> TransactionService
-> TransactionCacheRepository
-> TransactionCache entity
-> transactions_cache table
```

If you are confused about a feature, always trace it from controller to service to repository.

## 14. How Validation Works

Request classes use Jakarta Validation annotations.

Examples:

```java
@NotBlank
@Email
@Size
@DecimalMin
@NotNull
```

Controllers use:

```java
@Valid @RequestBody
```

If validation fails, Spring throws `MethodArgumentNotValidException`, and `GlobalExceptionHandler` returns a JSON validation error.

## 15. How Database Writes Work

Services use:

```java
@Transactional
```

This means:

- all database changes inside the method are one transaction
- if an exception is thrown, changes roll back
- if the method succeeds, changes commit

Read-only methods use:

```java
@Transactional(readOnly = true)
```

This is for queries.

## 16. Current Limitations

These are intentional for now:

- Wallet generation depends on the internal on-chain wallet API.
- Transaction signing depends on the internal on-chain signing API.
- No real private keys are stored.
- No Docker Compose is included because Trac owns orchestration.
- No real blockchain node is required for tests.
- No admin registration endpoint; default admin bootstrap is used.
- Balance updates are trusted from on-chain events when provided.

## 17. Tests

Folder:

```text
src/test
```

Files:

```text
MoneyFormatterTest.java
TransactionServiceTest.java
OnChainEventServiceTest.java
OnChainWalletServiceTest.java
OnChainTransactionSignerTest.java
RabbitMempoolPublisherTest.java
mockito-extensions/org.mockito.plugins.MockMaker
```

### `MoneyFormatterTest`

Checks canonical money format:

```text
50 -> 50.00
50.1 -> 50.10
```

### `TransactionServiceTest`

Checks important validation:

- cannot stake more than available balance
- cannot unstake while validator mode is active
- invalid transactions are not published to RabbitMQ

### `OnChainEventServiceTest`

Checks event handling:

- successful donation updates transaction, campaign, and user
- successful campaign create opens campaign

### Mockito MockMaker File

File:

```text
src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker
```

Value:

```text
mock-maker-subclass
```

This avoids Mockito inline self-attach issues on the local JDK 25 setup.

## 18. Suggested Study Order

Follow this exact order:

```text
1. README.md
2. CODE_GUIDE.md
3. pom.xml
4. src/main/resources/application.yml
5. src/main/resources/db/migration/V1__initial_schema.sql
6. src/main/java/com/tcl/backend/BackendApiApplication.java
7. common package
8. users package
9. security package
10. auth package
11. campaigns package
12. transactions package
13. messaging package
14. config package
15. tests
```

After that, study by feature:

```text
Register/login
Create campaign
Admin approve campaign
Donate
Stake
Unstake
Validator toggle
On-chain event update
```

## 19. How To Explain This Project In Class

Short version:

```text
My part is the Spring Boot off-chain backend. It exposes REST APIs for users, admins, campaigns, and transactions. It stores off-chain data in PostgreSQL, publishes signed transaction messages to RabbitMQ for the blockchain nodes, and listens to blockchain result events to synchronize campaign and user state.
```

Technical version:

```text
The backend follows a controller-service-repository architecture. Controllers handle HTTP requests, services enforce business rules, repositories persist JPA entities to PostgreSQL, and the messaging package bridges Web2 to Web3 through RabbitMQ. Outbound transactions are cached as PENDING in transactions_cache before being published to mempool_queue. On-chain confirmations arrive through event_bus_queue and are applied by OnChainEventService.
```

## 20. Where To Modify Later

Common future changes:

```text
Change wallet generation:
-> messaging/OnChainWalletService.java

Change transaction signing:
-> messaging/OnChainTransactionSigner.java

Change outbound JSON shape:
-> messaging/OutboundTransactionMessage.java
-> messaging/RabbitMempoolPublisher.java
-> messaging/NonceService.java

Change inbound event shape:
-> messaging/EventBusMessage.java
-> messaging/EventBusEventType.java
-> messaging/OnChainEventService.java

Add a new transaction type:
-> transactions/TransactionType.java
-> db/migration new Flyway file
-> TransactionService.java
-> OnChainEventService.java

Add a new API:
-> create request/response DTO
-> add controller method
-> add service method
-> add repository query if needed
-> add test
```

## 21. Mental Model

Remember this:

```text
PostgreSQL is the off-chain application state.
RabbitMQ mempool_queue is the outbound bridge to blockchain.
RabbitMQ event_bus_queue is the inbound bridge from blockchain.
transactions_cache is the audit trail for off-chain requests.
pending_balance is the escrow state while a transaction waits.
JWT proves who is calling the API.
Roles decide what the caller can do.
Services are where business rules live.
```

If you understand those lines, the rest of the code becomes much easier to read.
