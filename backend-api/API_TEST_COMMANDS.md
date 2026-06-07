# API Test Commands

Terminal commands for testing the Transparent Charity Ledger backend.

## Base URL

If backend runs on your own computer:

```bash
export BASE=http://localhost:8080
```

If backend runs on another computer, use that computer's network IP:

```bash
export BASE=http://172.16.133.172:8080
```

## Public Campaign Checks

```bash
curl -i "$BASE/api/v1/campaigns"
```

```bash
curl -i "$BASE/api/v1/campaigns?page=1&limit=5"
```

```bash
curl -i "$BASE/api/v1/campaigns?status=PENDING"
```

```bash
curl -i "$BASE/api/v1/campaigns?sort=title,asc"
```

Invalid status test:

```bash
curl -i "$BASE/api/v1/campaigns?status=NOPE"
```

## Register And Login

Register:

```bash
curl -i -X POST "$BASE/api/v1/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","email":"alice@example.com","password":"Password123"}'
```

Login:

```bash
curl -i -X POST "$BASE/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"Password123"}'
```

Set the user token from the login/register response:

```bash
export TOKEN="PASTE_ACCESS_TOKEN_HERE"
```

## User Profile

```bash
curl -i "$BASE/api/v1/users/me" \
  -H "Authorization: Bearer $TOKEN"
```

## Create Campaign

```bash
curl -i -X POST "$BASE/api/v1/campaigns" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Terminal Test Campaign",
    "description": "Created from curl",
    "coverImageUrl": "https://example.com/image.jpg",
    "targetAmount": 1000
  }'
```

Set the campaign id from the response:

```bash
export CAMPAIGN_ID="PASTE_CAMPAIGN_ID_HERE"
```

Campaign detail:

```bash
curl -i "$BASE/api/v1/campaigns/$CAMPAIGN_ID"
```

Campaign donors:

```bash
curl -i "$BASE/api/v1/campaigns/$CAMPAIGN_ID/donors"
```

## Admin Login

```bash
curl -i -X POST "$BASE/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@tcl.local","password":"Admin123!"}'
```

Set the admin token:

```bash
export ADMIN_TOKEN="PASTE_ADMIN_ACCESS_TOKEN_HERE"
```

## Approve Or Reject Campaign

Approve campaign:

```bash
curl -i -X PUT "$BASE/api/v1/admin/campaigns/$CAMPAIGN_ID/approve" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

Create another campaign first, then set its id:

```bash
export REJECT_CAMPAIGN_ID="PASTE_OTHER_CAMPAIGN_ID_HERE"
```

Reject campaign:

```bash
curl -i -X PUT "$BASE/api/v1/admin/campaigns/$REJECT_CAMPAIGN_ID/reject" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

## Transactions

Transaction history:

```bash
curl -i "$BASE/api/v1/transactions/me" \
  -H "Authorization: Bearer $TOKEN"
```

Stake:

```bash
curl -i -X POST "$BASE/api/v1/transactions/stake" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"amount":10}'
```

Unstake:

```bash
curl -i -X POST "$BASE/api/v1/transactions/unstake" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"amount":5}'
```

Toggle validator:

```bash
curl -i -X POST "$BASE/api/v1/transactions/validator/toggle" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"action":"OPT_IN"}'
```

Donate:

```bash
curl -i -X POST "$BASE/api/v1/transactions/donate" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"campaignId\": \"$CAMPAIGN_ID\",
    \"amount\": 25,
    \"note\": \"curl donation test\"
  }"
```

## Expected Error Tests

Missing token:

```bash
curl -i "$BASE/api/v1/users/me"
```

Invalid register body:

```bash
curl -i -X POST "$BASE/api/v1/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"username":"","email":"bad","password":"123"}'
```

Normal user tries admin route:

```bash
curl -i -X PUT "$BASE/api/v1/admin/campaigns/$CAMPAIGN_ID/approve" \
  -H "Authorization: Bearer $TOKEN"
```

## Database Checks

Open Postgres shell. Use the container name that exists on the machine.

```bash
docker exec -it charity_db_5433 psql -U admin -d charity_db
```

If the container is named `tcl-postgres` instead:

```bash
docker exec -it tcl-postgres psql -U admin -d charity_db
```

Latest accounts:

```sql
SELECT id, username, email, role, created_at
FROM accounts
ORDER BY created_at DESC
LIMIT 10;
```

User balances:

```sql
SELECT account_id, available_balance, pending_balance, staked_balance, is_active_validator
FROM users;
```

Latest campaigns:

```sql
SELECT id, title, status, target_amount, current_raised, created_by, approved_by
FROM campaigns
ORDER BY created_at DESC
LIMIT 10;
```

Latest transactions:

```sql
SELECT id, type, status, amount, user_id, campaign_id, tx_hash, created_at
FROM transactions_cache
ORDER BY created_at DESC
LIMIT 10;
```

Inject test balance:

```sql
UPDATE users
SET available_balance = 1000.00
WHERE account_id = 'PASTE_USER_ID_HERE';
```

Exit Postgres:

```sql
\q
```
