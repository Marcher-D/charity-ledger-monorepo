package network;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import app.Wallet;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * NodeOrchestrator - Standalone HTTP Server to dynamically spawn blockchain nodes.
 *
 * It exposes `POST /api/orchestrator/spawn_node` which:
 * 1. Derives a new node's deterministic wallet (Public Key & Address).
 * 2. Spawns an independent node — either an OS-level process (`java -cp ... app.Main`)
 *    or a sibling Docker container, depending on NODE_LAUNCH_MODE.
 * 3. Returns the derived keys to the backend.
 *
 * Environment configuration (defaults preserve the original local-testbed behavior):
 *   ORCHESTRATOR_PORT  HTTP port of this server                 (default: 9000)
 *   TOTAL_NODES        totalNodes arg passed to spawned nodes   (default: 10)
 *   MEMPOOL_API_URL    mempool HTTP base URL passed to nodes    (default: http://localhost:8080)
 *   EVENT_BUS_URL      RabbitMQ AMQP URL passed to nodes        (default: amqp://localhost:5672)
 *   NODE_LAUNCH_MODE   "process" | "docker"                     (default: process)
 *   NODE_IMAGE         Docker image for nodes (docker mode)     (default: charity-node:latest)
 *   DOCKER_NETWORK     Docker network nodes join (docker mode)  (default: charity_net)
 *   NODES_DATA_VOLUME  named volume mounted at /app/nodes       (default: nodes_data)
 *   NODES_DIR          where this process keeps its state file  (default: nodes)
 *
 * In docker mode every node runs in its own container named "node-<idx>" with its
 * own IP on DOCKER_NETWORK; peer discovery inside the node resolves "node-<i>:900<i>"
 * via NODE_HOST_PATTERN (see GenesisConfig.getSeedPeers).
 *
 * The next node index is persisted to <NODES_DIR>/orchestrator_next_index so an
 * orchestrator restart never re-assigns an index that is already bound to a
 * user account in the off-chain database.
 */
public class NodeOrchestrator {

    private static final AtomicInteger nextNodeIndex = new AtomicInteger(0);

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v;
    }

    public static void main(String[] args) throws IOException {
        int    port          = Integer.parseInt(env("ORCHESTRATOR_PORT", "9000"));
        int    totalNodes    = Integer.parseInt(env("TOTAL_NODES", "10"));
        String mempoolApiUrl = env("MEMPOOL_API_URL", "http://localhost:8080");
        String eventBusUrl   = env("EVENT_BUS_URL", "amqp://localhost:5672");
        String launchMode    = env("NODE_LAUNCH_MODE", "process");
        String nodeImage     = env("NODE_IMAGE", "charity-node:latest");
        String dockerNetwork = env("DOCKER_NETWORK", "charity_net");
        String nodesVolume   = env("NODES_DATA_VOLUME", "nodes_data");
        Path   nodesDir      = Path.of(env("NODES_DIR", "nodes"));
        Path   indexFile     = nodesDir.resolve("orchestrator_next_index");

        Files.createDirectories(nodesDir);
        nextNodeIndex.set(loadPersistedIndex(indexFile));

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/api/orchestrator/spawn_node", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if ("POST".equals(exchange.getRequestMethod())) {
                    try {
                        int nodeIndex = nextNodeIndex.getAndIncrement();
                        persistIndex(indexFile, nextNodeIndex.get());

                        // 1. Generate identity deterministically exactly like Node.java does
                        Wallet wallet = Wallet.fromSeedPhrase("GENESIS_NODE_" + (nodeIndex + 1));
                        String pubKey = wallet.getPublicKeyBase64();
                        String address = wallet.getAddress();
                        int nodePort = 9000 + nodeIndex;

                        // 2. Spawn the node independently (process or docker container)
                        List<String> command = new ArrayList<>();
                        if ("docker".equalsIgnoreCase(launchMode)) {
                            command.addAll(List.of(
                                "docker", "run", "-d",
                                "--name", "node-" + nodeIndex,
                                "--hostname", "node-" + nodeIndex,
                                "--network", dockerNetwork,
                                "-e", "NODE_HOST_PATTERN=node-%d",
                                "-v", nodesVolume + ":/app/nodes",
                                nodeImage,
                                String.valueOf(nodeIndex), String.valueOf(totalNodes),
                                mempoolApiUrl, eventBusUrl
                            ));
                        } else {
                            command.addAll(List.of(
                                "java", "-cp", "target/blockchain.jar",
                                "app.Main", String.valueOf(nodeIndex), String.valueOf(totalNodes),
                                mempoolApiUrl, eventBusUrl
                            ));
                        }

                        ProcessBuilder pb = new ProcessBuilder(command);
                        pb.redirectErrorStream(true);
                        pb.redirectOutput(new File("node_" + nodeIndex + "_orchestrated.log"));
                        Process process = pb.start();
                        System.out.println("[Orchestrator] Spawned Node " + nodeIndex
                            + " on port " + nodePort
                            + " (mode=" + launchMode + ", PID " + process.pid() + ")");

                        // 3. Return JSON response to the backend
                        String jsonResponse = String.format(
                            "{\n  \"status\": \"success\",\n  \"nodeIndex\": %d,\n  \"port\": %d,\n  \"publicKey\": \"%s\",\n  \"address\": \"%s\"\n}",
                            nodeIndex, nodePort, pubKey, address
                        );

                        byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, responseBytes.length);
                        OutputStream os = exchange.getResponseBody();
                        os.write(responseBytes);
                        os.close();

                    } catch (Exception e) {
                        e.printStackTrace();
                        String error = "{\"error\": \"" + e.getMessage() + "\"}";
                        exchange.sendResponseHeaders(500, error.getBytes().length);
                        OutputStream os = exchange.getResponseBody();
                        os.write(error.getBytes());
                        os.close();
                    }
                } else {
                    exchange.sendResponseHeaders(405, -1); // Method Not Allowed
                }
            }
        });

        // Lightweight readiness probe for docker-compose healthchecks and the
        // backend's genesis bootstrap retry loop.
        server.createContext("/api/orchestrator/health", exchange -> {
            byte[] body = ("{\"status\":\"ok\",\"nextNodeIndex\":" + nextNodeIndex.get() + "}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        server.setExecutor(null); // creates a default executor
        server.start();
        System.out.println("Node Orchestrator started on port " + port
            + " | mode=" + launchMode
            + " | totalNodes=" + totalNodes
            + " | nextNodeIndex=" + nextNodeIndex.get()
            + " | mempool=" + mempoolApiUrl
            + " | eventBus=" + eventBusUrl);
    }

    private static int loadPersistedIndex(Path indexFile) {
        try {
            if (Files.exists(indexFile)) {
                return Integer.parseInt(Files.readString(indexFile).trim());
            }
        } catch (Exception e) {
            System.err.println("[Orchestrator] Could not read index file, starting at 0: " + e.getMessage());
        }
        return 0;
    }

    private static synchronized void persistIndex(Path indexFile, int value) {
        try {
            Files.writeString(indexFile, String.valueOf(value));
        } catch (IOException e) {
            System.err.println("[Orchestrator] Could not persist index file: " + e.getMessage());
        }
    }
}
