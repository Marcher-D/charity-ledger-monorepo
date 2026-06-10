# Transparent Charity Ledger Backend API

Spring Boot off-chain backend for Khoi's role in the Transparent Charity Ledger project.

## What Is Implemented

- JWT auth with public register/login and protected profile route.
- PostgreSQL schema managed by Flyway for `accounts`, `users`, `admins`, `campaigns`, and `transactions_cache`.
- `users.pending_balance` escrow state from the project lead update logs, used to lock money while on-chain confirmation is pending.
- Campaign APIs for public browsing, search, sort, donor summaries, user campaign requests, and admin approve/reject.
- Transaction APIs for donate, stake, unstake, validator toggle, and user transaction history.
- RabbitMQ integration boundaries:
  - outbound `mempool_exchange` -> `mempool_queue`
  - inbound `event_bus_queue`
  - dead-letter queues `mempool_dlq` and `event_bus_dlq`
- Outbound transaction JSON includes `nonce_order` before `type`.
- Inbound Event Bus JSON supports `BLOCK_SUCCESS`, `BLOCK_FAIL`, `TX_FAIL`, and `TX_EXPIRED`.
- REST-backed on-chain wallet/signing adapters. The backend asks the on-chain service for wallet addresses and transaction signatures; private keys never enter the backend.
- Dockerfile for the backend API container.

## Run Locally

Requirements:

- Java 21+
- Maven 3.9+
- PostgreSQL
- RabbitMQ
- Internal on-chain wallet/signing service

The backend runs on:

```text
http://localhost:8080
```

If PostgreSQL and RabbitMQ are not already running, the quickest local setup is Docker:

```bash
docker run --name tcl-postgres \
  -e POSTGRES_DB=charity_db \
  -e POSTGRES_USER=admin \
  -e POSTGRES_PASSWORD=secretpassword \
  -p 5432:5432 \
  -d postgres:16
```

```bash
docker run --name tcl-rabbitmq \
  -e RABBITMQ_DEFAULT_USER=admin \
  -e RABBITMQ_DEFAULT_PASS=admin \
  -p 5672:5672 \
  -p 15672:15672 \
  -d rabbitmq:3-management
```

Then start the Spring Boot app:

```bash
cd backend-api
mvn spring-boot:run
```

Default database connection:

```text
jdbc:postgresql://localhost:5432/charity_db
username: admin
password: secretpassword
```

Default RabbitMQ connection:

```text
host: localhost
port: 5672
username: admin
password: admin
```

Default RabbitMQ contract:

```text
exchange: mempool_exchange
outbound queue/routing key: mempool_queue
outbound DLQ: mempool_dlq
inbound event queue: event_bus_queue
inbound DLQ: event_bus_dlq
```

Default on-chain wallet/signing service connection:

```text
base URL: http://localhost:9090
header: X-Internal-Api-Key: dev-secret
timeout: 5000 ms
```

Override it with:

```text
APP_ONCHAIN_WALLET_BASE_URL=http://localhost:9090
APP_ONCHAIN_INTERNAL_API_KEY=dev-secret
APP_ONCHAIN_TIMEOUT_MS=5000
```

If you need to restart the Docker services later:

```bash
docker start tcl-postgres tcl-rabbitmq
```

## Frontend Setup

For a local Vite frontend, set:

```text
VITE_API_BASE_URL=http://localhost:8080
```

The backend allows browser requests from these local frontend origins by default:

```text
http://localhost:5173
http://localhost:3000
```

Override them with:

```text
APP_CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:3000
```

The detailed API contract for frontend calls is in:

```text
FRONTEND_API_GUIDE.md
```

## Default Admin

On startup, the app creates an admin account if it does not exist:

```text
email: admin@tcl.local
password: Admin123!
```

Override or disable it with:

```text
APP_BOOTSTRAP_ADMIN_ENABLED=false
APP_BOOTSTRAP_ADMIN_EMAIL=...
APP_BOOTSTRAP_ADMIN_PASSWORD=...
```

## Main Endpoints

```text
POST /api/v1/auth/register
POST /api/v1/auth/login
GET  /api/v1/users/me

GET  /api/v1/campaigns?status=&q=&sort=&page=&limit=
GET  /api/v1/campaigns/{id}
GET  /api/v1/campaigns/{id}/donors
POST /api/v1/campaigns
PUT  /api/v1/admin/campaigns/{id}/approve
PUT  /api/v1/admin/campaigns/{id}/reject

POST /api/v1/transactions/donate
POST /api/v1/transactions/stake
POST /api/v1/transactions/unstake
POST /api/v1/transactions/validator/toggle
GET  /api/v1/transactions/me
```

Protected requests use:

```text
Authorization: Bearer <JWT>
```

## Money Flow

For spend-like actions (`DONATE`, `STAKE`, `WITHDRAW` later), the backend first moves money from `available_balance` to `pending_balance`. For `UNSTAKE`, it moves money from `staked_balance` to `pending_balance`.

Then the Event Bus decides the final result:

- `BLOCK_SUCCESS`: transaction becomes `SUCCESS`; pending money is settled.
- `TX_FAIL` or `TX_EXPIRED`: transaction becomes `FAILED`; pending money is refunded.
- `BLOCK_FAIL` with `SLASH_STAKE`: proposer stake is reduced.

Supported transaction types now include:

```text
DONATE, REFUND, CREATE, STAKE, UNSTAKE, VALIDATOR_TOGGLE,
REWARD, SLASH, DEPOSIT, WITHDRAW
```

## Verify

```bash
mvn test
```

Current focused tests cover canonical money formatting, transaction validation before publishing, pending-balance refunds, Event Bus success updates, on-chain wallet/signing REST calls, and RabbitMQ publish shape.
