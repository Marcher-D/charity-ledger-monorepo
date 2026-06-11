# Classroom load rehearsal (30–50 users)

Before letting a 30–50 person class register on the site, rehearse the load on
the actual host machine. Every registration spawns a real node container, so
the rehearsal exercises exactly what class day will: Docker memory, the
orchestrator spawn path, the backend connection pool, and consensus health
under ~N² gossip.

## Prerequisites

1. Docker Desktop → Settings → Resources → **Memory: 12 GB** (16 GB Mac).
2. Stack running: `docker compose --profile build build && docker compose up -d`,
   wait until the site works at http://localhost:3000.
3. Close heavy apps (browsers with many tabs, IDEs) — macOS keeps ~4 GB.

## Run

```bash
./scripts/load-rehearsal.sh                # ramps to 20 → 35 → 50 users
STAGES="10 20" ./scripts/load-rehearsal.sh # smaller custom ramp
```

The script registers fake users (`rehearsal_<runid>_<n>`), each deposits 100
TCL, and after each stage it settles ~2 minutes and reports:

- node container count and total container memory vs the Docker VM ceiling,
- consensus rounds completed and how many were BOTTOM (empty) — the
  struggling-consensus signal,
- mempool depth (backlog means transactions are queuing faster than blocks
  drain them).

It **aborts automatically** at >85 % Docker memory or >4 BOTTOM rounds per 20 —
the stage it stops at is the practical ceiling of the machine.

If consensus struggles before memory runs out, the first suspect is
`network/TokenBucketRateLimiter` (10 msg/s per peer IP) dropping relayed votes
under gossip load — raising its refill rate is a network-infra change (not
consensus logic) and should be its own tested change.

## Results (measured 2026-06-12, M-series Mac, 16 GB, Docker VM 12 GB)

First run — exposed and fixed two scaling bugs (see below):

| Stage (users) | Node containers | Container mem | Consensus |
|---------------|-----------------|---------------|-----------|
| 20 | 40 | 6.7 GB (56 %) | healthy during ramp — live deposit settled ~60 s, donation 10 s |
| 35 | 54 | 8.2 GB (68 %) | **wedged** (BBA panic loop, rate-limiter bans active) |
| 50 | 69 | 9.8 GB (81 %) | wedged; memory fine |

Re-test after the fixes (rate-limiter raised, staggered respawn):

| Phase | Nodes | Consensus |
|-------|-------|-----------|
| staggered join | 13 → 48 | **healthy**: 16 rounds / 10 min, 1 BOTTOM, 0 bans, real blocks committing |
| full network | 69 | wedged again (BBA iteration desync; no bans — pure gossip latency) |

**Bugs found and fixed by this rehearsal:**

1. `TokenBucketRateLimiter` allowed only 10 msg/s per peer IP — at ~50 nodes the
   validators' own vote relays got 60-s bans, votes were dropped, consensus
   wedged. Raised to 100 msg/s refill / 1000 burst (env: `RATE_LIMIT_REFILL_PER_SEC`,
   `RATE_LIMIT_BURST`) — still blocks brute-force floods.
2. Synchronized mass boot: the orchestrator respawned all nodes at once after a
   restart; ~60 observers booting together desync the validators' BBA iterations
   and wedge consensus for 20+ minutes. The orchestrator now **staggers** respawns
   (genesis first, then one node per `NODE_RESPAWN_STAGGER_MS`, default 3000), and
   the backend retries stake-replay signing while late nodes come up.

**Practical ceiling on this machine: ~50 node containers ≈ 40 classmates**
(5 genesis + campaigns + 1 node per registered user). Up to that point rounds
close in 25–60 s under churn and transactions settle end-to-end. Beyond ~55–60
nodes the full-mesh gossip (every node relays every vote to every peer, O(N²))
outruns the BBA vote windows and consensus wedges regardless of memory or CPU
headroom. Lifting that ceiling needs a gossip-layer change (e.g. observers not
relaying votes in a full mesh) — an on-chain network change, deliberately NOT
made here.

**Class-day recommendation:** for 30–40 people, run as-is. For ~50, split into
two waves or have some pairs share an account, and watch
`docker logs node-0` for `BOTTOM round` frequency as people join.

## Cleanup (DESTRUCTIVE — do this before class day)

The rehearsal fills PostgreSQL with fake accounts and the host with ~50 node
containers. The reset wipes **all** accounts, including real ones:

```bash
docker rm -f $(docker ps -aq --filter "name=node-")
docker compose down -v
docker volume rm nodes_data
docker compose up -d
```

(`down -v` removes the compose-managed postgres volume; `nodes_data` is
fixed-name and must be removed explicitly.)
