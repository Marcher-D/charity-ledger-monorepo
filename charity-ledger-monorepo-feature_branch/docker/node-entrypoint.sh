#!/bin/sh
# Node container entrypoint.
# Args: <nodeIndex> <totalNodes> <mempoolApiUrl> <eventBusUrl>
set -e

NODE_INDEX="${1:?nodeIndex required}"
TOTAL_NODES="${2:-5}"
MEMPOOL_API_URL="${3:-http://localhost:8080}"
EVENT_BUS_URL="${4:-amqp://localhost:5672}"
SIGNER_PORT="${SIGNER_PORT:-7100}"

echo "[entrypoint] node=$NODE_INDEX total=$TOTAL_NODES mempool=$MEMPOOL_API_URL eventbus=$EVENT_BUS_URL signer=:$SIGNER_PORT"

# Signing service in the background (same identity derivation as the node).
java -cp blockchain.jar network.NodeSignerService "$NODE_INDEX" "$SIGNER_PORT" &

# Consensus node in the foreground (container lives and dies with it).
exec java -cp blockchain.jar app.Main "$NODE_INDEX" "$TOTAL_NODES" "$MEMPOOL_API_URL" "$EVENT_BUS_URL"
