# On-Chain Wallet and Signing API Contract

This document describes the internal REST API that the on-chain/wallet service should provide for the backend.

The backend owns:

- user registration/login
- campaign APIs
- transaction cache rows
- RabbitMQ publishing through `mempool_exchange` with routing key `mempool_queue`
- event-bus settlement from `event_bus_queue`

The on-chain/wallet service owns:

- wallet/keypair creation
- private-key storage
- transaction signing
- signature/hash rules used by the mempool/blockchain side

Important rule:

```text
The backend must never receive or store private keys.
```

## Base URL and Authentication

Local default:

```text
http://localhost:9090
```

Every request from backend includes:

```http
X-Internal-Api-Key: <shared-secret>
Content-Type: application/json
```

Local backend default:

```text
APP_ONCHAIN_WALLET_BASE_URL=http://localhost:9090
APP_ONCHAIN_INTERNAL_API_KEY=dev-secret
APP_ONCHAIN_TIMEOUT_MS=5000
```

## 1. Health Check

Used by backend/manual tests to confirm the service is reachable.

```http
GET /internal/v1/health
X-Internal-Api-Key: <shared-secret>
```

Success response:

```json
{
  "status": "UP"
}
```

## 2. Create Wallet

Used when the backend needs a wallet address for a user or campaign.

```http
POST /internal/v1/wallets
X-Internal-Api-Key: <shared-secret>
Content-Type: application/json
```

### Request

For a user:

```json
{
  "ownerType": "USER",
  "ownerId": "eeff7d3f-8498-48cc-bc6f-37f7907b9ae1"
}
```

For a campaign:

```json
{
  "ownerType": "CAMPAIGN",
  "ownerId": "29a6d72c-4b45-4295-b6a5-8f1659a0f566"
}
```

Fields:

| Field | Type | Required | Description |
|---|---|---:|---|
| `ownerType` | string | yes | Must be `USER` or `CAMPAIGN`. This is metadata only, not blockchain logic. |
| `ownerId` | UUID string | yes | Backend UUID of the user account or campaign. |

### Success Response

```json
{
  "walletAddress": "0x21749a159423b627021514a7c3d6c7680002c6f6",
  "publicKey": "04abc...",
  "keyId": "wallet-key-123",
  "algorithm": "ECDSA_SECP256K1"
}
```

Fields:

| Field | Type | Required | Description |
|---|---|---:|---|
| `walletAddress` | string | yes | Public wallet/on-chain address. Backend stores this. |
| `publicKey` | string | recommended | Public key for this wallet. Backend does not currently store it, but it is useful for debugging. |
| `keyId` | string | recommended | Internal key identifier for the wallet service. |
| `algorithm` | string | recommended | Expected value: `ECDSA_SECP256K1`. |

### Required Behavior

- Same `ownerType + ownerId` should return the same wallet if called again.
- Different owners must not receive the same wallet address.
- Private key must never be returned.
- Private key must not appear in logs.
- `ownerType` is only for ownership separation and rules. The blockchain key itself does not care whether it belongs to a user or campaign.

## 3. Sign Transaction

Used before backend publishes a transaction message through RabbitMQ `mempool_exchange` with routing key `mempool_queue`.

```http
POST /internal/v1/transactions/sign
X-Internal-Api-Key: <shared-secret>
Content-Type: application/json
```

### Request

Example stake request:

```json
{
  "txId": "9f16a0fe-4e2f-4ed0-8c42-c28bae7e8bb9",
  "nonce_order": 123,
  "type": "STAKE",
  "senderAddress": "0x21749a159423b627021514a7c3d6c7680002c6f6",
  "targetAddress": "STAKE_POOL",
  "amount": "10.00",
  "timestamp": 1778832956997,
  "payload": {
    "campaignId": null,
    "note": "STAKE"
  }
}
```

Example campaign creation request:

```json
{
  "txId": "9eccb151-c473-436c-b921-e0a171e2da10",
  "nonce_order": 124,
  "type": "CREATE",
  "senderAddress": "0xuserwallet...",
  "targetAddress": "0xcampaignwallet...",
  "amount": "0.00",
  "timestamp": 1778832957997,
  "payload": {
    "campaignId": "29a6d72c-4b45-4295-b6a5-8f1659a0f566",
    "note": "CREATE_CAMPAIGN"
  }
}
```

Fields:

| Field | Type | Required | Description |
|---|---|---:|---|
| `txId` | UUID string | yes | Backend transaction ID from `transactions_cache.id`. |
| `nonce_order` | integer | yes | Globally increasing backend nonce/order value. |
| `type` | string | yes | Transaction type, such as `CREATE`, `DONATE`, `STAKE`, `UNSTAKE`, `VALIDATOR_TOGGLE`. |
| `senderAddress` | string | yes | Wallet address of the account starting the transaction. |
| `targetAddress` | string | yes | Campaign wallet, `STAKE_POOL`, or `VALIDATOR_REGISTRY`. |
| `amount` | string | yes | Decimal string with two places, for example `"10.00"`. |
| `timestamp` | integer | yes | Backend-created epoch milliseconds. |
| `payload` | object | yes | Extra transaction-specific fields. |

### Success Response

```json
{
  "publicKey": "04abc...",
  "signature": "3045...",
  "txHash": "f99736343465113ffd3991049999a720a49af7f7e4de805ba2773ec5b015c6ae",
  "algorithm": "ECDSA_SECP256K1",
  "signedAt": 1778832957001
}
```

Fields:

| Field | Type | Required | Description |
|---|---|---:|---|
| `publicKey` | string | yes | Public key used by mempool/on-chain side to verify the signature. |
| `signature` | string | yes | Signature over the transaction data. |
| `txHash` | string | yes | Fingerprint/hash of the signed transaction data. |
| `algorithm` | string | recommended | Expected value: `ECDSA_SECP256K1`. |
| `signedAt` | integer | recommended | Epoch milliseconds when the wallet service signed. |

### Required Behavior

- `senderAddress` must already exist in the wallet service.
- Signing must use the private key for `senderAddress`.
- The signing service and mempool/blockchain verifier must use the same canonical transaction format.
- The backend does not recompute or verify the signature. It stores `txHash` and publishes the returned fields to RabbitMQ.
- If the service cannot sign, return non-2xx. Backend will not publish the transaction.

## 4. Backend RabbitMQ Message After Signing

After signing, the backend publishes this shape to RabbitMQ:

```text
exchange: mempool_exchange
routing key: mempool_queue
queue: mempool_queue
```

```json
{
  "txId": "9f16a0fe-4e2f-4ed0-8c42-c28bae7e8bb9",
  "nonce_order": 123,
  "type": "STAKE",
  "senderAddress": "0x21749a159423b627021514a7c3d6c7680002c6f6",
  "targetAddress": "STAKE_POOL",
  "amount": "10.00",
  "timestamp": 1778832956997,
  "payload": {
    "campaignId": null,
    "note": "STAKE"
  },
  "publicKey": "04abc...",
  "signature": "3045...",
  "txHash": "f99736343465113ffd3991049999a720a49af7f7e4de805ba2773ec5b015c6ae"
}
```

Important:

```text
The fields sent to /internal/v1/transactions/sign are the same transaction fields published to RabbitMQ.
```

The on-chain side should verify `signature`, `publicKey`, and `txHash` against the transaction data.

## 5. Error Expectations

Recommended error responses:

```json
{
  "error": "Invalid request",
  "message": "senderAddress is unknown"
}
```

Recommended status codes:

| Status | Meaning |
|---:|---|
| `200` | Success |
| `400` | Invalid request body |
| `401` or `403` | Missing/invalid internal API key |
| `404` | Unknown wallet/sender address |
| `409` | Ownership conflict |
| `500` | Internal wallet/signing failure |
| `503` | Service temporarily unavailable |

Backend handling:

- Any non-2xx response during wallet creation/signing is treated as service failure.
- Missing `walletAddress` from wallet response becomes `502 Bad Gateway`.
- Missing `publicKey`, `signature`, or `txHash` from signing response becomes `502 Bad Gateway`.

## 6. Acceptance Checklist

The on-chain/wallet service is ready when:

- `POST /internal/v1/wallets` returns a wallet for `USER`.
- `POST /internal/v1/wallets` returns a wallet for `CAMPAIGN`.
- Same `ownerType + ownerId` returns the same wallet.
- Private key is never returned.
- `POST /internal/v1/transactions/sign` returns `publicKey`, `signature`, and `txHash`.
- Unknown `senderAddress` cannot sign.
- Mempool can verify the signature using the same transaction data.
- Backend can register a user without using fake wallet generation.
- Backend can approve a campaign and publish a signed `CREATE` transaction.
