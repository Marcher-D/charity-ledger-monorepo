# Backend Presentation Plan — Transparent Charity Ledger

Target: ~15–20 minute talk + Q&A. Slides/whiteboard only — no source code.
Your part: the backend. Teammates cover frontend, blockchain core, Docker.

**The one sentence to repeat until everyone can say it back:**
> The backend is a *trusted mirror*: PostgreSQL is the source of truth for the web
> experience, the blockchain is the source of truth for money movement — and the
> backend's whole job is keeping the two honest with each other.

---

## 1. What the backend is (2 min)

Anchor diagram — draw it once, keep it visible the whole talk:

```
Browser ──> Frontend ──> BACKEND ──> PostgreSQL          (web truth)
                            │  ▲
         "create a node"    │  │  outcomes & rewards
                            ▼  │
                      Orchestrator ──> node-0 … node-N
                            │               ▲  "sign this"
        signed transactions ▼               │
        ──── message queue ──> mempool <── nodes pull work
        <─── message queue ─── every node announces results   (chain truth)
```

The backend has **exactly three jobs**:
1. **Web platform** — accounts, login, campaigns, dashboards (a classic REST API over a database).
2. **Outbound** — turn a button click into a *correctly signed* blockchain transaction.
3. **Inbound** — listen to what consensus actually decided, and settle balances accordingly.

Everything else in the talk is just these three jobs in detail.

---

## 2. Identity architecture: one person = two identities (2 min)

| | Web identity | Chain identity |
|---|---|---|
| What it is | email + password → JWT session | Ed25519 keypair + wallet address |
| Where it lives | backend database | **that user's own node container** |
| What it proves | "I may use the website" | "I authorize moving my money" |

Key design decision to present proudly:
- **When a user registers, the backend asks the orchestrator to launch a brand-new
  blockchain node just for them.** That node holds their private key. Campaigns get the
  same treatment when approved — a campaign *is* an on-chain account.
- The backend stores only the **public** half (address, public key, node number) to know
  *who to ask* for signatures — never the private key.

> Security headline: the backend cannot spend anyone's money, even if fully compromised,
> because it has nothing to sign with. (One exception in §4.)

---

## 3. The escrow model: three balances, one state machine (3 min)

Every user's money is split into three buckets:

```
AVAILABLE  ── spendable now
PENDING    ── locked: an action is awaiting consensus
STAKED     ── locked into validator stake
```

Every money action follows the same lifecycle — this is the core process of the backend:

```
user clicks "Donate 100"
        │
   1. validate (enough available? campaign open?)
   2. move 100: AVAILABLE → PENDING          ← money is now locked
   3. record the action as PENDING
   4. send the signed transaction to the chain
        │
        ├── chain says VALIDATED → PENDING → applied (campaign +100, status SUCCESS)
        └── chain says REJECTED  → PENDING → refunded back to AVAILABLE (status FAILED)
```

Why this matters (say it as a claim): **the backend never shows money in two places at
once, and never loses money to a timing window.** While consensus is deciding, the funds
are frozen — you can't double-spend them through the website. It's effectively a
two-phase commit between the database and the blockchain.

---

## 4. The signing architecture: who signs what (4 min — the technical heart)

Two signing paths, one rule: **every transaction is signed by its actual sender.**

```
USER ACTIONS (donate / stake / unstake / withdraw)
  backend builds the transaction fields
      → asks the SENDER'S OWN NODE to sign it (private key never leaves the node)
      → publishes the signed transaction

SYSTEM ACTION (deposit only)
  deposits mint money into the chain, so the sender is the chain's SYSTEM ACCOUNT
      → the backend holds that one system keypair and signs deposits itself
```

Two points to make:

1. **Canonical agreement.** The backend and the chain must agree *byte for byte* on what
   gets signed: a fixed field order, a fixed number format (2 decimals), a hash of that
   string as the transaction ID. If even one byte differs, the chain rejects the
   signature. This contract is pinned by unit tests so it can't silently drift.

2. **Action mapping** (good slide):

| Web action | On-chain transaction | Signed by |
|---|---|---|
| Deposit | DEPOSIT (system → user) | backend (system key) |
| Donate | DONATE (user → campaign, 2% fee to block proposer) | user's node |
| Stake / Unstake | STAKE / UNSTAKE | user's node |
| Withdraw | WITHDRAW (user → system) | user's node |
| Campaign approval | no chain transaction — spawns the campaign's node, settles instantly | — |

---

## 5. The two message highways (2 min)

Both are one-way queues over the same broker — decoupling is the point:

- **Outbound (backend → chain):** the backend drops the signed transaction JSON onto a
  queue; a mempool service feeds it to nodes. The backend doesn't know or care *which*
  node picks it up — nodes can restart freely without losing submissions.
- **Inbound (chain → backend):** nodes announce three kinds of facts:
  - `TXN_VALIDATED` — "this transaction is final" → settle SUCCESS
  - `TXN_REJECTED` — "this transaction failed/expired" → settle FAILED + refund
  - `BLOCK_FINALIZED` — "round done; this proposer earned this reward" → credit 2% of the
    round's donations to the proposer. **No block data crosses this boundary** — the
    backend mirrors outcomes, it does not sync the chain.

---

## 6. The duplicate-storm problem (3 min — best "war story" section)

Here's the catch nobody sees coming: **every node announces every event.**
16 nodes online = 16 copies of "transaction X validated". After a node restarts and
replays history, it announces everything *again*.

The backend's answer is layered idempotency — "don't prevent duplicates, make every
consumer immune to them":

```
Layer 1: deterministic event IDs        → same fact ⇒ same ID ⇒ recognizable repeat
Layer 2: the PENDING guard              → a settled record cannot settle twice
Layer 3: rewards ledger in the database → one reward credit per block, survives restarts
```

Audience takeaway: in distributed systems, *exactly-once delivery doesn't exist* —
exactly-once **effect** is engineered at the consumer.

---

## 7. Restart resilience: the chain is ephemeral, the database is durable (3 min)

Hard constraint inherited from BFT consensus: a chain killed mid-vote cannot safely
resume (votes live in memory). Instead of fighting it, the design embraces it:

**Every startup runs this sequence:**

```
1. wipe chain state, start a FRESH chain (round 0), bump the "chain epoch" counter
2. relaunch one node per known user/campaign
3. backend notices the new epoch  →  REPLAYS every user's balance onto the fresh chain
       (one system deposit per user, then re-stake what they had staked)
4. a cleanup job watches for orphans: any action still PENDING after 15 minutes
       (its message died with the shutdown) is failed and refunded automatically
```

Claims you can make because they were actually tested:
- Stop everything mid-donation, start again → balances reconcile, the interrupted
  donation refunds itself. **No corrupted or stuck money.**
- Stop a *single* node → the rest keep finalizing (4 of 5 validators is enough);
  when it returns it re-verifies and replays every block it missed, then rejoins.

The epoch counter is the safety pin: replay happens exactly once per fresh chain, even
if the backend crashes mid-replay.

---

## 8. Security model — honest version (2 min)

- Stateless JWT auth; user vs admin roles; correct 401 (not logged in) vs 403 (wrong role).
- Defensive lessons learned in real testing (tell one as a story):
  - a leftover login token from an older database must never block creating a new
    account → broken tokens are treated as "anonymous", not as errors;
  - browsers send extra headers on writes that they don't send on reads — our origin
    rules originally blocked every *other device on the WiFi* from posting anything,
    while page loads worked fine. Asymmetric failures are the worst to debug.
- **Trust boundaries, stated honestly:**
  - what the backend *can* do alone: render any number on the website (it's trusted UI);
  - what it *cannot* do alone: move on-chain money — that needs the owner's node;
  - what nobody can do: rewrite settled chain history (BFT supermajority).

---

## 9. Process walkthrough to narrate live (slide with 6 arrows, no code) (2 min)

"The life of a donation" — narrate end to end, pointing at the anchor diagram:

```
 click Donate(100)
 → backend locks 100 (available→pending), writes PENDING
 → backend asks donor's node to sign; publishes signed tx
 → mempool hands it to validators; consensus finalizes it in a block
 → every node announces TXN_VALIDATED (+ BLOCK_FINALIZED with the proposer's 2% reward)
 → backend (deduplicating) settles: pending→gone, campaign +100, proposer +2
 → dashboard polls and flips PENDING → SUCCESS  (~10–30 seconds total)
```

If a live demo is allowed, do exactly this in the browser with the dashboard visible —
no terminals needed. Otherwise a 30-second screen recording of it loops nicely.

---

## 10. Q&A preparation

- **Why a message queue instead of calling the chain directly?**
  Decoupling and buffering: any node may pick up work, nodes can restart freely, and
  submissions survive temporary outages. No request/response coupling to consensus timing.
- **Why doesn't the backend store blocks?**
  Separation of truth: it needs outcomes, not history. Storing blocks would duplicate
  the chain's job and create divergence risk.
- **What stops the backend from faking balances?**
  On the website — nothing; it's the trusted mirror (be upfront!). But it cannot move
  on-chain funds without the owner's node, and chain state is independently verifiable.
- **What if the same event arrives twice / two nodes disagree?**
  Layered idempotency (§6); consensus guarantees nodes don't disagree on finalized facts.
- **What happens to money in flight during a crash?**
  Locked in PENDING, then auto-failed and refunded by the 15-minute cleanup job.
- **Why do new validators take a while after staking?**
  Chain rule (stake only counts after a ~320-round lookback window — flash-loan defense);
  the backend just reflects it.
- **Why exactly 5 genesis nodes?**
  They're the only pre-funded accounts at chain genesis; everyone else starts at zero and
  must deposit → stake, which is exactly the flow the backend implements.

---

## 11. Time budget & closing

| Section | Minutes |
|---|---|
| What the backend is | 2 |
| Two identities | 2 |
| Escrow state machine | 3 |
| Signing architecture | 4 |
| Message highways + duplicates | 4 |
| Restart resilience | 3 |
| Security & trust model | 2 |
| Life of a donation | 2 |

**Close with three sentences:**
1. The backend never holds a user's key — every transaction is signed by its owner's own node.
2. Every balance change is escrowed as PENDING until consensus confirms or rejects it — no double-spends, no stuck money.
3. The whole system is restart-proof by design: the database is durable, the chain is replayable, and in-flight money refunds itself.
