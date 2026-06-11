#!/bin/bash
# Classroom load rehearsal: register fake users in ramped stages and measure
# whether the host can carry them BEFORE class day.
#
# Each registration spawns a real node container (exactly what happens when a
# classmate signs up), then the user deposits once so transactions flow through
# consensus under load.
#
# Usage:
#   ./scripts/load-rehearsal.sh                 # stages 20 35 50
#   STAGES="10 20" ./scripts/load-rehearsal.sh  # custom stages (cumulative users)
#
# Env knobs: BASE_URL (default http://localhost:3000 — same nginx path real
# users take), SETTLE_SECONDS (default 120 — measurement window per stage).
#
# CLEANUP AFTERWARDS IS MANUAL AND DESTRUCTIVE (wipes ALL accounts, real ones
# included) — see docs/LOAD_REHEARSAL.md.
set -u

BASE_URL="${BASE_URL:-http://localhost:3000}"
STAGES="${STAGES:-20 35 50}"
SETTLE_SECONDS="${SETTLE_SECONDS:-120}"
RUN_ID="$(date +%H%M%S)"
PASSWORD="Rehearsal123!"

# Abort thresholds (the whole point is to find limits safely)
MEM_ABORT_PCT=85          # of Docker VM memory
BOTTOM_ABORT_PER_20=4     # >4 BOTTOM rounds per 20 = consensus is struggling

docker_vm_total_mb() {
  docker info --format '{{.MemTotal}}' 2>/dev/null | awk '{printf "%d", $1/1048576}'
}

containers_mem_mb() {
  # Sum of live container usage, MiB/GiB normalized to MB
  docker stats --no-stream --format '{{.MemUsage}}' 2>/dev/null \
    | awk -F'[ /]' '{v=$1} v ~ /GiB/ {sub(/GiB/,"",v); s+=v*1024} v ~ /MiB/ {sub(/MiB/,"",v); s+=v} END {printf "%d", s}'
}

node_count() { docker ps -q --filter "name=node-" | wc -l | tr -d ' '; }

# Rounds and BOTTOM rounds on node-0 (a genesis validator) since a timestamp
rounds_since()  { docker logs node-0 --since "$1" 2>&1 | grep -c "Halting Condition"; }
bottoms_since() { docker logs node-0 --since "$1" 2>&1 | grep -c "BOTTOM round — creating empty block"; }

mempool_depth() {
  curl -s --max-time 5 http://localhost:8081/api/mempool/batch 2>/dev/null \
    | python3 -c 'import sys,json;
try: print(len(json.load(sys.stdin)))
except Exception: print("?")' 2>/dev/null || echo "?"
}

register_and_deposit() {
  local i="$1"
  local user="rehearsal_${RUN_ID}_${i}"
  local start end token http
  start=$(python3 -c 'import time; print(time.time())')
  http=$(curl -s -o /tmp/rehearsal_resp.json -w '%{http_code}' --max-time 30 \
    -X POST "$BASE_URL/api/v1/auth/register" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"$user\",\"email\":\"$user@rehearsal.local\",\"password\":\"$PASSWORD\"}")
  end=$(python3 -c 'import time; print(time.time())')
  local secs
  secs=$(python3 -c "print(f'{$end-$start:.1f}')")
  if [ "$http" != "200" ] && [ "$http" != "201" ]; then
    echo "  user $i: REGISTER FAILED (HTTP $http, ${secs}s): $(head -c 200 /tmp/rehearsal_resp.json)"
    return 1
  fi
  token=$(python3 -c 'import json; print(json.load(open("/tmp/rehearsal_resp.json"))["accessToken"])' 2>/dev/null)
  echo "  user $i: registered in ${secs}s"
  curl -s -o /dev/null --max-time 30 -X POST "$BASE_URL/api/v1/transactions/deposit" \
    -H 'Content-Type: application/json' -H "Authorization: Bearer $token" \
    -d '{"amount": 100}'
}

report() {
  local label="$1" since="$2"
  local total used pct nodes rounds bottoms
  total=$(docker_vm_total_mb); used=$(containers_mem_mb)
  pct=$(( total > 0 ? used * 100 / total : 0 ))
  nodes=$(node_count)
  rounds=$(rounds_since "$since"); bottoms=$(bottoms_since "$since")
  echo ""
  echo "── $label ─────────────────────────────────"
  echo "  node containers : $nodes"
  echo "  container memory: ${used} MB of ${total} MB Docker VM (${pct}%)"
  echo "  rounds in window: $rounds   BOTTOM (empty): $bottoms"
  echo "  mempool depth   : $(mempool_depth)"
  if [ "$pct" -ge "$MEM_ABORT_PCT" ]; then
    echo "  *** ABORT: memory ${pct}% >= ${MEM_ABORT_PCT}% — this is the ceiling. ***"
    return 1
  fi
  if [ "$rounds" -eq 0 ]; then
    echo "  *** ABORT: 0 rounds finalized in the window — consensus is STALLED. ***"
    echo "      (check 'docker logs node-0' for BBA panic loops / [RateLimit] bans)"
    return 1
  fi
  if [ "$rounds" -ge 10 ] && [ $(( bottoms * 20 / rounds )) -gt "$BOTTOM_ABORT_PER_20" ]; then
    echo "  *** ABORT: ${bottoms}/${rounds} BOTTOM rounds — consensus is struggling. ***"
    return 1
  fi
  return 0
}

echo "Load rehearsal run $RUN_ID — stages: $STAGES (cumulative users), settle ${SETTLE_SECONDS}s"
echo "Target: $BASE_URL   Docker VM: $(docker_vm_total_mb) MB"
registered=0

for target in $STAGES; do
  echo ""
  echo "================ STAGE: ramp to $target users ================"
  while [ "$registered" -lt "$target" ]; do
    registered=$((registered + 1))
    register_and_deposit "$registered" || true
    sleep 2   # gentler than humanly possible mass sign-up; still spawns 1 node each
  done
  # Unix epoch: docker --since misreads zone-less wall-clock strings as local time
  stage_since=$(date +%s)
  echo "  ...settling ${SETTLE_SECONDS}s to measure steady state..."
  sleep "$SETTLE_SECONDS"
  if ! report "after $target users" "$stage_since"; then
    echo ""
    echo "Rehearsal stopped at $registered users — that is the practical ceiling."
    exit 1
  fi
done

echo ""
echo "All stages passed. The host carries $registered users."
echo "REMEMBER: clean up the fake accounts before class day (docs/LOAD_REHEARSAL.md)."
