# Transparent Charity Ledger — Integrated Monorepo

A charity donation platform where every donation, stake and reward settles on a
real Algorand-style proof-of-stake blockchain running locally in Docker.

| Part | Where | What it does |
|---|---|---|
| Frontend | `frontend/` | React/Vite SPA (served by nginx on :3000) |
| Backend | `backend-api/` | Spring Boot REST API, PostgreSQL, RabbitMQ publisher/consumer |
| On-chain | `charity-ledger-monorepo-feature_branch/` | PPOS blockchain nodes, NodeOrchestrator, mock mempool |
| Glue | `docker-compose.yml` | Runs everything; the orchestrator spawns one container per blockchain node |

## Prerequisites

- **Docker Desktop** (macOS, or Windows with the WSL2 backend) — that's it.
  Java, Node and Python are NOT needed on your machine; everything builds inside Docker.
- Give Docker Desktop at least **8 GB RAM / 4 CPUs** (Settings → Resources).
  The system runs 6 services plus one JVM container per blockchain node.
- Free ports: `3000` (web), `8080` (API), `5432` (postgres), `5672`/`15672` (rabbitmq), `8081` (mempool debug), `7001` (orchestrator).

## Run

```bash
git clone https://github.com/Marcher-D/charity-ledger-monorepo.git
cd charity-ledger-monorepo
git checkout project/merge-v2

# 1. Build all images, including the blockchain node image the orchestrator spawns
docker compose --profile build build

# 2. Start everything
docker compose up -d
```

Wait ~30–60 seconds: the backend boots, asks the orchestrator to spawn the
**5 genesis validator nodes** (`node-0`…`node-4`), and consensus starts
producing blocks. Watch it live with `docker logs -f node-0`.

Then open **http://localhost:3000**.

- Admin login: `admin@tcl.local` / `Admin123!`
- Register any user → a dedicated blockchain node container is spawned for them.

### Demo flow

1. Register a user → Dashboard.
2. **Deposit TCL** — funds your on-chain account via a system-signed DEPOSIT
   (takes ~10–30 s to confirm through consensus; refresh to see SUCCESS).
3. Create a campaign → log in as admin → approve it (spawns the campaign's node).
4. **Donate / Stake / Unstake / Withdraw** from the dashboard — every action is
   signed by *your own node*, goes through the RabbitMQ mempool, gets finalized
   by BFT consensus, and the proposer earns 2% of donations as a reward.

## Share it over WiFi (host for friends)

One person runs the stack (steps above); everyone on the same WiFi uses it —
phones included. The API is served same-origin through the frontend's nginx,
so no configuration is needed on anyone's device.

1. On the host, find the WiFi IP address:
   - macOS: `ipconfig getifaddr en0` (or System Settings → Wi-Fi → Details)
   - Windows: `ipconfig` → IPv4 Address of the Wi-Fi adapter
2. Everyone opens `http://<host-ip>:3000` (e.g. `http://192.168.1.25:3000`).

Each registration spawns a blockchain node **on the host machine**, so only
the host pays the CPU cost. If others can't connect: allow Docker through the
host's firewall (Windows: allow for *Private* networks when prompted), and
make sure both devices are on the same network (guest WiFi often isolates
devices).

## Stop / Restart

```bash
# Node containers are spawned by the orchestrator, NOT by compose — remove them too:
docker rm -f $(docker ps -aq --filter "name=node-")
docker compose down
```

**Restarting a single node is safe** (and a fun thing to demo): with the rest
of the stack running, `docker stop node-2` … `docker start node-2`. The other
validators keep finalizing rounds while it is down (4 of 5 still clear the 68%
threshold); on start it resumes from its own database, the CatchupService
re-verifies and replays every missed block, and it rejoins live consensus in
about a minute. User/campaign nodes do the same but return to **observer
mode** (see below).

**Restarting is safe**: accounts, campaigns and balances live in PostgreSQL and
survive. The blockchain itself starts **fresh at round 0 on every boot**
(`CHAIN_RESET_ON_START=true`) — the consensus core only reliably supports
nodes that boot together, so resuming a half-killed chain is not attempted.
On startup the orchestrator respawns one container per known node, and the
backend detects the new "chain epoch" and **replays every user's balance onto
the fresh chain** (system deposits + re-stakes), so the web mirror and the
chain stay consistent. Transactions that were in flight during a shutdown are
automatically failed and refunded after 15 minutes.

**Full reset (fresh chain + empty database):**

```bash
docker rm -f $(docker ps -aq --filter "name=node-")
docker compose down -v
docker volume rm nodes_data
docker compose --profile build build && docker compose up -d
```

## How the pieces talk

```
Browser ──> frontend(:3000) ──> backend(:8080) ──> PostgreSQL
                                   │  ▲
              spawn_node (HTTP)    │  │ TXN_VALIDATED / TXN_REJECTED /
                                   ▼  │ BLOCK_FINALIZED (rewards)
                            orchestrator(:7000) ── docker run ──> node-0..node-N
                                   │                   ▲   │ sign (HTTP :7100)
        signed tx JSON             │                   │   ▼
backend ──> RabbitMQ mempool_queue ──> mock mempool(:8080) <── nodes poll batches
nodes  ──> RabbitMQ event_bus_queue ──> backend updates balances/campaigns
```

- Every transaction is **Ed25519-signed by its own sender's node** (the backend
  never holds user private keys). Only DEPOSIT is signed by the backend with the
  chain's system key.
- Only the first **5 genesis nodes** have balance/stake at round 0; every later
  user/campaign node starts at 0/0 and must deposit first.
- **Observer vs validator:** a node only participates in consensus when it has
  sortition-eligible stake. New user/campaign nodes catch up to the chain tip
  (re-verifying every block certificate) and then *listen* for finalized blocks
  in observer mode. After a user stakes, the protocol's R−320 lookback (a
  flash-loan defense) means the node automatically flips to validator about
  320 rounds after the STAKE commits and can then propose blocks and earn
  rewards.

## Troubleshooting

- **Windows:** clone with git (don't download a ZIP edited by other tools) —
  `.gitattributes` keeps shell scripts LF so the Docker images work. Use the
  WSL2 backend of Docker Desktop.
- **macOS port 7000:** taken by AirPlay, which is why the orchestrator maps to
  host `7001`. Inside Docker it is still `orchestrator:7000`.
- **Queues broken after pulling new code:** old RabbitMQ volumes can hold
  stale queue definitions — do the full reset above.
- RabbitMQ management UI: http://localhost:15672 (admin/admin).
- Database shell: `docker exec -it charity_db psql -U admin -d charity_db`.
