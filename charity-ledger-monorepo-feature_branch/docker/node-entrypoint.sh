#!/bin/bash
# Node container entrypoint.
# Args: <nodeIndex> <totalNodes> <mempoolApiUrl> <eventBusUrl>
set -e

NODE_INDEX="${1:?nodeIndex required}"
TOTAL_NODES="${2:-5}"
MEMPOOL_API_URL="${3:-http://localhost:8080}"
EVENT_BUS_URL="${4:-amqp://localhost:5672}"
SIGNER_PORT="${SIGNER_PORT:-7100}"

echo "[entrypoint] node=$NODE_INDEX total=$TOTAL_NODES mempool=$MEMPOOL_API_URL eventbus=$EVENT_BUS_URL signer=:$SIGNER_PORT"

# Wait for RabbitMQ before starting the node. The EventGateway inside the node
# now reconnects lazily too, but starting cleanly avoids dropping the first
# events when the whole stack boots at once.
# amqp://user:pass@host:port -> host, port
HOSTPORT="${EVENT_BUS_URL##*@}"
HOSTPORT="${HOSTPORT#amqp://}"
MQ_HOST="${HOSTPORT%%:*}"
MQ_PORT="${HOSTPORT##*:}"
MQ_PORT="${MQ_PORT%%/*}"
for i in $(seq 1 60); do
  if (exec 3<>"/dev/tcp/${MQ_HOST}/${MQ_PORT}") 2>/dev/null; then
    exec 3>&- 3<&-
    echo "[entrypoint] RabbitMQ reachable at ${MQ_HOST}:${MQ_PORT}"
    break
  fi
  echo "[entrypoint] waiting for RabbitMQ at ${MQ_HOST}:${MQ_PORT} ($i/60)..."
  sleep 2
done

# Both JVMs get heap ceilings: without them each defaults to ~25% of the
# Docker VM's memory, and a dozen node containers starve the rest of the
# stack until the OOM killer takes out the backend/RabbitMQ.
#
# The ceiling is UNIFORM for every node (configurable via NODE_HEAP):
# validator-ness is dynamic — any node becomes a validator once its stake
# enters the sortition lookback — so memory must not depend on node index.
# Ceilings are not reservations: an observer with a 256m ceiling still only
# uses ~150m; validators get the GC breathing room they need (GC pauses on
# a starved validator delay votes, which is what produces empty rounds).
NODE_HEAP="${NODE_HEAP:-256m}"

# Signing service in the background (same identity derivation as the node).
java -Xms16m -Xmx64m -cp blockchain.jar network.NodeSignerService "$NODE_INDEX" "$SIGNER_PORT" &

# Consensus node in the foreground (container lives and dies with it).
exec java -Xms32m -Xmx"$NODE_HEAP" -XX:MaxGCPauseMillis=50 \
     -cp blockchain.jar app.Main "$NODE_INDEX" "$TOTAL_NODES" "$MEMPOOL_API_URL" "$EVENT_BUS_URL"
