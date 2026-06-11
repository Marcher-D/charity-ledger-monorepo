# Backend Presentation Plan — Transparent Charity Ledger

Target: ~15–20 minutes talk + 5 min demo + Q&A.
Your part: `backend-api/` (Spring Boot). Teammates cover frontend, blockchain core, Docker.
Golden thread to repeat throughout: **"The backend is a trusted mirror: PostgreSQL is the
source of truth for the *web experience*, the blockchain is the source of truth for *money
movement* — and the backend's whole job is keeping the two honest with each other."**

---

## 1. Opening — what the backend is (2 min)

Draw / show this and keep it on screen as your anchor:

```
Browser ──> frontend(:3000) ──> BACKEND(:8080) ──> PostgreSQL  (web truth)
                                   │  ▲
              spawn_node (HTTP)    │  │ TXN_VALIDATED / TXN_REJECTED /
                                   ▼  │ BLOCK_FINALIZED (proposer reward)
                            orchestrator ── docker run ──> node-0..node-N
                                   │                  ▲  sign (HTTP :7100)
        signed tx JSON             ▼                  │
        RabbitMQ mempool_queue ──> mock mempool <── nodes poll batches
        RabbitMQ event_bus_queue <─ every node      (chain truth)
```

Say: the backend has **exactly three jobs**:
1. Classic web app: auth, users, campaigns, REST API (Spring Boot + PostgreSQL).
2. **Outbound**: turn user actions into correctly signed on-chain transactions.
3. **Inbound**: listen to what consensus actually decided and settle balances accordingly.

Tech stack one-liner: Spring Boot 3 / Spring Security (JWT), PostgreSQL + Flyway
migrations, RabbitMQ (two one-way links), BouncyCastle (Ed25519), Docker.

---

## 2. Domain model — the escrow design (3 min)

Open `backend-api/src/main/resources/db/migration/` (V1→V3 tell the story of the project).

Key tables (show in psql or just on a slide):

| Table | Why it exists |
|---|---|
| `accounts` / `users` | login identity vs. wallet identity; users carry `wallet_address`, `public_key`, `node_index` — the link to *their own* blockchain node |
| `campaigns` | also have `public_key` + `node_index` — a campaign IS an on-chain account |
| `transactions_cache` | every money action, with `tx_hash` = the on-chain txId (the correlation key) and a status machine: `PENDING → SUCCESS / FAILED` |
| `genesis_nodes` | the 5 bootstrap validators (idempotency marker — restart-safe) |
| `processed_rewards` | dedup for proposer rewards (more in §6) |
| `onchain_sync` | the "chain epoch" the backend last reconciled against (§7) |

**The escrow model** (this is the part people remember):
every user has three balances — `available`, `pending`, `staked`.
When you donate 100, the backend does NOT subtract 100 from available and call it done.
It *moves 100 from available → pending* and writes a PENDING row. Money in `pending` is
locked: you can't spend it twice while consensus is still deciding. Only a chain event
moves it out of pending — forward (SUCCESS) or back (FAILED = refund).
This is a mini two-phase commit between Postgres and the blockchain.

Code to show: `transactions/TransactionService.java` — pick ONE method (donate) and walk
it: validate → lock funds → build canonical tx → get signature → publish → return PENDING.

---

## 3. Security (3 min)

File: `security/SecurityConfig.java`, `security/JwtAuthenticationFilter.java`.

- **Stateless JWT**: no sessions; every request carries `Authorization: Bearer`. The filter
  validates and loads the account; roles split USER vs ADMIN routes
  (`/api/v1/admin/**` → ADMIN only; campaign approval lives there).
- **401 vs 403 done right**: custom `AuthenticationEntryPoint` (not logged in → 401 JSON)
  and `AccessDeniedHandler` (logged in, wrong role → 403 JSON). Worth one sentence: Spring's
  default error dispatch re-enters the security chain and can rewrite a 403 into a 401 —
  we write the 403 response directly to avoid that.
- **Battle scars you can tell as stories** (each was a real bug found during live testing):
  - A *stale token* (from before a DB reset) used to kill even the public register endpoint
    → the filter now treats any broken/orphaned token as "anonymous" instead of erroring.
  - Browsers send an `Origin` header on every POST → the localhost-only CORS whitelist
    403'd every device on the WiFi except the host. GETs worked, POSTs didn't — that
    asymmetry made it maddening to debug. Now origin *patterns* (default `*`, Bearer auth,
    no cookies, so this is safe).

**The most important security claim of the whole design** (say it slowly):
> The backend can never spend a user's money on its own, because it never holds user
> private keys. Every user transaction is signed by that user's own node.

---

## 4. The signing architecture — two paths (4 min, the technical heart)

Files: `messaging/NodeSignerClient.java`, `messaging/SystemKeySigner.java`,
`messaging/OnChainTransactionMessage.java`.

1. **User-signed path** (DONATE / STAKE / UNSTAKE / WITHDRAW):
   backend builds the transaction *fields*, then calls the **sender's own node**
   (`http://node-<idx>:7100/api/signer/sign`). The node derives its key, computes the
   canonical txId, signs, returns `{txId, signature}`. The private key never leaves the
   node container. (Retry loop because a freshly spawned node's JVM needs a few seconds.)

2. **System-signed path** (DEPOSIT only):
   deposits create money out of thin air (the faucet), so the *chain's system account* is
   the sender. The backend holds that one keypair — derived deterministically from the
   seed `SYSTEM_DEPOSIT_KEY_V1`, same derivation as the chain (sha256 → Ed25519). Show
   `SystemKeySigner`: it reproduces the chain's canonical signable string **byte for byte**:

   ```
   type|senderPubKey|receiverPubKey|%.2f amount|firstValid|lastValid|timestamp|note
   txId = sha256Hex(that string);  signature = Ed25519 over its UTF-8 bytes
   ```

   One wrong byte (we standardized on `%.2f`, US locale) and the chain rejects the
   signature. There's a unit test pinning a known txId/signature vector
   (`SystemKeySignerTest`) so canonicalization drift fails the build, not the demo.

Type mapping table (good slide):

| Web action | On-chain type | Sender signs |
|---|---|---|
| Deposit | DEPOSIT | backend (system key) |
| Donate | DONATE (2% fee → block proposer) | user's node |
| Stake / Unstake | STAKE / UNSTAKE | user's node |
| Withdraw | WITHDRAW (receiver = system) | user's node |
| Campaign approval | *off-chain* (spawns campaign node, settles immediately) | — |

---

## 5. Outbound messaging — backend → chain (2 min)

Files: `messaging/RabbitMempoolPublisher.java`, `messaging/RabbitConfig.java`.

- The published message is the **exact JSON the chain's `Transaction` class deserializes** —
  no translation layer on the node side.
- Queues are **classic durable** on purpose: the chain's `EventGateway` declares them that
  way, and RabbitMQ hard-fails (`PRECONDITION_FAILED`) if two parties declare the same
  queue with different types. (Real bug: backend originally declared *quorum* queues —
  events silently died.)
- The on-chain `txId` is stored as `tx_hash` on the PENDING row → this is how the answer
  finds its question later.

---

## 6. Inbound events — chain → backend, and the duplicate-storm problem (3 min)

Files: `messaging/OnChainEventListener.java`, `messaging/OnChainEventService.java`.

Three event types arrive on `event_bus_queue`:
- `TXN_VALIDATED {txId,...}` → find row by tx_hash → settle SUCCESS (apply per-type:
  credit deposit, move stake, bump campaign raised…)
- `TXN_REJECTED {txId, reason}` → FAILED + **refund** the pending lock
- `BLOCK_FINALIZED {round, blockHash, proposer, rewardAmount}` → credit the proposer
  2% of that round's donations. **No block data is transferred or stored** — it's a
  reward notification, not block sync.

**The problem to highlight**: *every* node publishes these events — 16 nodes = 16 copies
of everything (plus re-publishes when a node replays history after a restart). Idempotency
is therefore layered:
1. deterministic eventId (UUID derived from txId+type) + in-memory seen-set,
2. the `status == PENDING` guard — a settled row can't settle twice,
3. `processed_rewards` table — DB-level, restart-proof dedup for reward credits
   (one row per blockHash; genesis proposers map to no user → logged and skipped).

Punchline: "we don't try to prevent duplicates — we make every consumer idempotent."

---

## 7. Resilience — restarts are a feature (3 min)

Files: `config/GenesisNodeInitializer.java`, `config/ChainStateReconciler.java`,
`transactions/PendingTransactionExpiryJob.java`.

The consensus core cannot safely resume a chain that was killed mid-round (votes live in
RAM). Design decision: **the chain is ephemeral, PostgreSQL is durable.**

On every startup, in order:
1. Orchestrator wipes chain files, bumps the **chain epoch**, respawns every known node.
2. `GenesisNodeInitializer` (idempotent, `genesis_nodes` marker): first boot only, spawns
   the 5 funded validators.
3. `ChainStateReconciler` sees `epoch != last reconciled` → **replays every user's balance
   onto the fresh chain**: system DEPOSIT of (available+pending+staked), wait, then
   node-signed re-STAKE of the staked part. Marks the epoch *first* so a crash mid-replay
   can't double-fund.
4. `PendingTransactionExpiryJob` (scheduled): any PENDING row older than 15 min (its
   in-flight message died with a shutdown) → FAILED + refund. No stuck money, ever.

Demo-able claim: "you can `docker compose down` mid-donation, come back up, and the
worst case is that donation auto-refunds after 15 minutes — balances never corrupt."

---

## 8. Live demo script (5 min — rehearse once!)

Pre-demo: stack started (`docker compose up -d`), wait ~1 min, have two terminals ready.

1. Register a user in the browser → terminal: `docker ps | grep node-` — **a new node
   container appeared for that user** (audience-visible cause and effect).
2. Deposit 1000 → dashboard shows PENDING → flips to SUCCESS in ~10–30 s.
   Meanwhile show `docker logs -f charity_backend` — point at the
   `TXN_VALIDATED` consumption line.
3. Donate to a campaign → show campaign `currentRaised` going up, and (in logs) the
   `BLOCK_FINALIZED` reward line ("Proposer reward 2.00 for … — skipped (genesis)").
4. Peek at the database:
   `docker exec -it charity_db psql -U admin -d charity_db`
   → `SELECT type, status, amount, tx_hash FROM transactions_cache ORDER BY created_at DESC LIMIT 5;`
5. (If time, the crowd-pleaser) `docker stop node-2` → donate again → still settles
   (4 of 5 validators ≥ 68%) → `docker start node-2` → show it catch up in its logs.

Fallback if live demo is risky: screen-record steps 1–3 beforehand and narrate.

---

## 9. Anticipated questions (have these answers ready)

- **"Why RabbitMQ instead of calling the chain directly?"** Decoupling + buffering: the
  backend doesn't care which node picks the tx up, survives nodes restarting, and gets
  natural retry semantics. Also matches the chain team's existing mempool/event design.
- **"Why doesn't the backend store blocks?"** Different sources of truth. The backend only
  needs transaction *outcomes* and reward notifications; storing blocks would duplicate
  the chain's job and invite divergence.
- **"What stops the backend from faking balances?"** On the web side, nothing — it's the
  trusted mirror (this is honest!). But it cannot *move on-chain money* without the
  owner's node signing, and anyone can verify on-chain state independently of the backend.
- **"What if two events conflict / arrive twice?"** Idempotency layers (§6); the PENDING
  guard means the first settlement wins and the rest are no-ops.
- **"Why does staking take so long to make me a validator?"** Chain-side rule (R−320
  stake lookback, flash-loan defense) — backend just reflects it.
- **"What happens to in-flight transactions on a crash?"** 15-minute expiry job: FAILED +
  refund (§7).
- **"Why exactly 5 genesis nodes?"** They're the only pre-funded/pre-staked accounts in
  the chain's genesis config; everyone else starts at 0 and must deposit → stake.

---

## 10. Suggested time budget

| Section | Minutes |
|---|---|
| Opening + diagram | 2 |
| Domain model & escrow | 3 |
| Security | 3 |
| Signing architecture | 4 |
| Messaging out + in (duplicates) | 4 |
| Resilience / restart story | 3 |
| Live demo | 5 |
| Q&A buffer | 5+ |

**Three sentences to end on:**
1. The backend never holds a user's key — users' own nodes sign everything.
2. Every balance change is escrowed PENDING until consensus confirms or rejects it.
3. The whole system is restart-proof: Postgres is durable, the chain is replayable,
   and stuck money refunds itself.
