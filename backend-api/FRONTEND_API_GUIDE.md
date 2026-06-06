# Frontend API Guide

This file explains how the frontend should call the Transparent Charity Ledger backend API.

## Base URL

Local backend default:

```text
http://localhost:8080
```

If the frontend runs on another computer, use the backend computer's network IP:

```text
http://<backend-machine-ip>:8080
```

Recommended frontend environment variable:

```text
VITE_API_BASE_URL=http://localhost:8080
```

All endpoints below are relative to the base URL.

## Request Rules

- Send JSON bodies with `Content-Type: application/json`.
- Protected endpoints require `Authorization: Bearer <accessToken>`.
- Public endpoints do not need a token.
- Do not send form-data or `x-www-form-urlencoded` bodies. Use raw JSON.
- `GET` requests do not need a body.
- Money values can be sent as JSON numbers, for example `10.50`.
- IDs are UUID strings.
- Timestamps are ISO strings from the backend.

Example protected headers:

```js
{
  "Accept": "application/json",
  "Content-Type": "application/json",
  "Authorization": `Bearer ${accessToken}`
}
```

## Auth Flow

1. User registers or logs in.
2. Backend returns `accessToken`, `tokenType`, and `expiresIn`.
3. Store `accessToken` on the frontend.
4. Send the token on every protected request.
5. If a protected request returns `401`, clear the token and send the user back to login.

Auth response shape:

```json
{
  "accessToken": "jwt-token-here",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

## Small Fetch Helper

```js
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

export async function apiRequest(path, options = {}) {
  const token = localStorage.getItem("accessToken");
  const hasBody = options.body !== undefined && options.body !== null;

  const headers = {
    Accept: "application/json",
    ...(hasBody ? { "Content-Type": "application/json" } : {}),
    ...(options.headers ?? {}),
    ...(token ? { Authorization: `Bearer ${token}` } : {})
  };

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers
  });

  const text = await response.text();
  let data = null;

  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = text;
  }

  if (!response.ok) {
    const message = typeof data === "object" && data !== null
      ? data.message ?? "API request failed"
      : response.statusText || "API request failed";
    const error = new Error(message);
    error.status = response.status;
    error.data = data;
    throw error;
  }

  return data;
}
```

Example:

```js
const campaigns = await apiRequest("/api/v1/campaigns?page=1&limit=10");
```

POST/PUT requests should stringify the JSON body:

```js
const result = await apiRequest("/api/v1/transactions/stake", {
  method: "POST",
  body: JSON.stringify({ amount: 10.00 })
});
```

## Public Endpoints

### Register

```text
POST /api/v1/auth/register
```

Body:

```json
{
  "username": "alice",
  "email": "alice@example.com",
  "password": "Password123"
}
```

Success: `201 Created`

Returns the auth response. Store `accessToken`.

Validation:

- `username` is required, max 100 characters.
- `email` is required and must be a valid email.
- `password` is required, 8 to 100 characters.

### Login

```text
POST /api/v1/auth/login
```

Body:

```json
{
  "email": "alice@example.com",
  "password": "Password123"
}
```

Success: `200 OK`

Returns the auth response. Store `accessToken`.

### List Campaigns

```text
GET /api/v1/campaigns?status=&q=&sort=&page=&limit=
```

Query parameters:

| Name | Required | Notes |
| --- | --- | --- |
| `status` | No | One of `PENDING`, `REJECTED`, `PROCESSING`, `OPEN`, `COMPLETED`. |
| `q` | No | Search text. |
| `sort` | No | Examples: `createdAt,desc`, `title,asc`, `targetAmount,desc`, `currentRaised,asc`. |
| `page` | No | 1-based page number. Default is `1`. |
| `limit` | No | Default is `10`. Backend clamps to max `100`. |

Response:

```json
{
  "items": [
    {
      "id": "b0b7d9ab-caf4-4b4b-9885-1936387046d8",
      "title": "School Supplies",
      "description": "Help buy supplies.",
      "coverImageUrl": "https://example.com/cover.jpg",
      "targetAmount": 1000.00,
      "currentRaised": 125.50,
      "status": "OPEN",
      "onChainAddress": "wallet-address",
      "createdBy": "8f52a8b3-2190-41c2-ae5b-aaea63de0621",
      "approvedBy": "aa5a35e6-4be6-486f-9464-2a6b28a1f4d3",
      "createdAt": "2026-05-16T08:00:00Z"
    }
  ],
  "page": 1,
  "limit": 10,
  "totalItems": 1,
  "totalPages": 1
}
```

### Get Campaign Detail

```text
GET /api/v1/campaigns/{id}
```

Returns one campaign object with the same shape as the list item above.

### Get Campaign Donors

```text
GET /api/v1/campaigns/{id}/donors
```

Response:

```json
[
  {
    "username": "alice",
    "amount": 25.00,
    "txHash": "0xabc123",
    "status": "SUCCESS",
    "createdAt": "2026-05-16T08:10:00Z"
  }
]
```

## Protected User Endpoints

These endpoints require:

```text
Authorization: Bearer <accessToken>
```

### Current User Profile

```text
GET /api/v1/users/me
```

Response:

```json
{
  "id": "8f52a8b3-2190-41c2-ae5b-aaea63de0621",
  "username": "alice",
  "email": "alice@example.com",
  "role": "USER",
  "walletAddress": "wallet-address",
  "availableBalance": 100.00,
  "pendingBalance": 0.00,
  "stakedBalance": 0.00,
  "activeValidator": false
}
```

Roles:

```text
USER
ADMIN
```

### Create Campaign

```text
POST /api/v1/campaigns
```

Body:

```json
{
  "title": "Clean Water Fund",
  "description": "Raising funds for clean water.",
  "coverImageUrl": "https://example.com/water.jpg",
  "targetAmount": 5000.00
}
```

Success: `201 Created`

Returns the created campaign. New campaigns start as `PENDING`.

Validation:

- `title` is required, max 255 characters.
- `description` is required.
- `coverImageUrl` is optional, max 500 characters.
- `targetAmount` is required and must be at least `0.01`.

### Donate

```text
POST /api/v1/transactions/donate
```

Body:

```json
{
  "campaignId": "b0b7d9ab-caf4-4b4b-9885-1936387046d8",
  "amount": 25.00,
  "note": "Happy to support"
}
```

Response:

```json
{
  "txId": "d939df7f-7419-454e-81b6-56c2f807b10e",
  "txHash": "0xabc123",
  "status": "PENDING",
  "message": "Donation is waiting for on-chain confirmation"
}
```

Frontend behavior after success:

- Show pending transaction state.
- Refresh `/api/v1/users/me` because balances may move from available to pending.
- Refresh `/api/v1/transactions/me`.

Notes:

- Donation only works when the campaign status is `OPEN`.
- User must have enough `availableBalance`.

### Stake

```text
POST /api/v1/transactions/stake
```

Body:

```json
{
  "amount": 10.00
}
```

Returns a transaction action response.

Frontend behavior after success:

- Refresh profile balances.
- Refresh transaction history.

### Unstake

```text
POST /api/v1/transactions/unstake
```

Body:

```json
{
  "amount": 10.00
}
```

Returns a transaction action response.

Notes:

- User must have enough `stakedBalance`.
- User cannot unstake while `activeValidator` is `true`.

### Toggle Validator

```text
POST /api/v1/transactions/validator/toggle
```

Body:

```json
{
  "action": "OPT_IN"
}
```

Allowed actions:

```text
OPT_IN
OPT_OUT
```

Returns a transaction action response.

Notes:

- `OPT_IN` requires `stakedBalance` greater than zero.
- This action is also confirmed asynchronously by the blockchain event flow.

### My Transactions

```text
GET /api/v1/transactions/me
```

Response:

```json
[
  {
    "id": "d939df7f-7419-454e-81b6-56c2f807b10e",
    "txHash": "0xabc123",
    "type": "DONATE",
    "campaignId": "b0b7d9ab-caf4-4b4b-9885-1936387046d8",
    "amount": 25.00,
    "note": "Happy to support",
    "status": "PENDING",
    "createdAt": "2026-05-16T08:10:00Z"
  }
]
```

Transaction statuses:

```text
PENDING
SUCCESS
FAILED
```

Common transaction types the frontend will see:

```text
DONATE
CREATE
STAKE
UNSTAKE
VALIDATOR_TOGGLE
```

Other backend-supported types:

```text
REFUND
REWARD
SLASH
DEPOSIT
WITHDRAW
```

## Admin Endpoints

Admin endpoints require a token for an account with role `ADMIN`.

Default local admin account:

```text
email: admin@tcl.local
password: Admin123!
```

### Approve Campaign

```text
PUT /api/v1/admin/campaigns/{id}/approve
```

Response:

```json
{
  "message": "Campaign approval is processing on-chain",
  "status": "PROCESSING",
  "transactionId": "d939df7f-7419-454e-81b6-56c2f807b10e"
}
```

Notes:

- Only `PENDING` campaigns can be approved.
- Approval changes the campaign to `PROCESSING`.
- The campaign becomes fully open only after the on-chain confirmation flow updates it.

### Reject Campaign

```text
PUT /api/v1/admin/campaigns/{id}/reject
```

Response:

```json
{
  "message": "Campaign rejected",
  "status": "REJECTED",
  "transactionId": null
}
```

Notes:

- Campaign can be rejected from `PENDING` or `PROCESSING`.

## Error Response Shape

Most application errors from controllers and services use this JSON shape:

```json
{
  "timestamp": "2026-05-16T08:15:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "validation": {
    "email": "must be a well-formed email address",
    "password": "size must be between 8 and 100"
  }
}
```

Frontend display rules:

- Use `message` for toast/banner errors.
- Use `validation` for field-level form errors when it is not `null`.
- Validation message text can vary by Java/runtime locale, so frontend code should key off field names and status codes instead of comparing exact validation strings.
- `401` usually means the token is missing, expired, or invalid. Security-generated `401` responses may not include the exact custom shape above, so handle them by status code too.
- `403` means the user is logged in but does not have permission.
- `404` means the resource does not exist.
- `409` means the requested action conflicts with current state, such as donating to a non-open campaign.

## Recommended Frontend Screens

- Login/register screen: call auth endpoints and store token.
- Campaign list: call public campaign list with filters and pagination.
- Campaign detail: call campaign detail and donor list.
- Create campaign form: protected, calls `POST /api/v1/campaigns`.
- Wallet/profile panel: protected, calls `GET /api/v1/users/me`.
- Transaction history: protected, calls `GET /api/v1/transactions/me`.
- Admin campaign review: admin token, list `PENDING` campaigns and call approve/reject.

## Important Integration Notes

- Transaction actions return `PENDING` first. The blockchain/event-bus flow updates them later to `SUCCESS` or `FAILED`.
- After donate/stake/unstake/toggle, refresh profile and transaction history.
- Public campaign browsing works without login. Creating campaigns and transactions requires login.
- Admin review should use an admin login, not a normal user login.
- The backend allows browser requests from `http://localhost:5173` and `http://localhost:3000` by default. For a different frontend origin, start the backend with `APP_CORS_ALLOWED_ORIGINS`, for example `APP_CORS_ALLOWED_ORIGINS=http://localhost:5173,http://192.168.1.20:5173`.
