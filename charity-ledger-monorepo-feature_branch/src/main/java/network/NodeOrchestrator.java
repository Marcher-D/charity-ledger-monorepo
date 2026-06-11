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
 *
 * On startup the orchestrator RESPAWNS every node it has ever assigned
 * (indexes 0..nextNodeIndex-1). Node containers are ephemeral — a stack
 * shutdown removes them — but their identities live in the off-chain database
 * and their chain state lives on the nodes volume, so without this respawn a
 * restarted system would have registered users whose nodes no longer exist
 * (no consensus, no signing — every transaction would hang or fail).
 */
public class NodeOrchestrator {

    private static final AtomicInteger nextNodeIndex = new AtomicInteger(0);
    // The chain's GenesisConfig funds/stakes exactly the first five identities;
    // they must boot together (no respawn stagger) so round 1 can certify.
    private static final int GENESIS_NODE_COUNT = 5;
    // Incremented on every CHAIN_RESET_ON_START wipe; exposed via /health so the
    // backend knows when to replay balances onto a fresh chain.
    private static final java.util.concurrent.atomic.AtomicLong chainEpoch =
            new java.util.concurrent.atomic.AtomicLong(0);

    /** Immutable launch configuration read from the environment once at startup. */
    private record Config(
            int totalNodes, String mempoolApiUrl, String eventBusUrl,
            String launchMode, String nodeImage, String dockerNetwork, String nodesVolume) {
        boolean dockerMode() { return "docker".equalsIgnoreCase(launchMode); }
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v;
    }

    public static void main(String[] args) throws IOException {
        int  port      = Integer.parseInt(env("ORCHESTRATOR_PORT", "9000"));
        Path nodesDir  = Path.of(env("NODES_DIR", "nodes"));
        Path indexFile = nodesDir.resolve("orchestrator_next_index");
        Path epochFile = nodesDir.resolve("chain_epoch");
        boolean resetChainOnStart = Boolean.parseBoolean(env("CHAIN_RESET_ON_START", "false"));

        Config config = new Config(
                Integer.parseInt(env("TOTAL_NODES", "10")),
                env("MEMPOOL_API_URL", "http://localhost:8080"),
                env("EVENT_BUS_URL", "amqp://localhost:5672"),
                env("NODE_LAUNCH_MODE", "process"),
                env("NODE_IMAGE", "charity-node:latest"),
                env("DOCKER_NETWORK", "charity_net"),
                env("NODES_DATA_VOLUME", "nodes_data")
        );

        Files.createDirectories(nodesDir);
        nextNodeIndex.set(loadPersistedIndex(indexFile));

        /*
         * CHAIN_RESET_ON_START: wipe all node chain databases so every node
         * boots together at round 0 — the only restart path the consensus
         * implementation reliably supports. Resuming a chain that was killed
         * mid-round deadlocks: panicked validators block in Recovery Mode
         * waiting for a certificate that the remaining stake (<68%) can never
         * produce. The chain_epoch counter tells the off-chain backend that a
         * reset happened so it can replay user balances onto the fresh chain.
         * Node identities are deterministic, so identities/addresses survive.
         */
        if (resetChainOnStart) {
            int wiped = wipeChainState(nodesDir);
            long epoch = bumpEpoch(epochFile);
            System.out.println("[Orchestrator] CHAIN_RESET_ON_START: wiped " + wiped
                    + " chain state file(s); chain epoch is now " + epoch);
        }
        chainEpoch.set(loadEpoch(epochFile));

        // Bring every previously assigned node back to life (see class javadoc).
        //
        // The genesis validators (first 5) start together so round 1 can
        // certify, but the remaining nodes are STAGGERED: measured live, ~60
        // observers booting simultaneously (each catching up + relaying every
        // vote) desync the validators' BBA iterations and wedge consensus for
        // tens of minutes, while the same nodes joining gradually are harmless.
        // Runs on its own thread so the HTTP API (spawn_node, /health) is up
        // immediately and the backend can bootstrap/register while we respawn.
        long respawnStaggerMs = Long.parseLong(env("NODE_RESPAWN_STAGGER_MS", "3000"));
        int known = nextNodeIndex.get();
        if (known > 0) {
            System.out.println("[Orchestrator] Respawning " + known + " previously assigned node(s)"
                    + (known > GENESIS_NODE_COUNT ? " (staggered " + respawnStaggerMs + "ms after genesis)" : "")
                    + "...");
            Thread respawner = new Thread(() -> {
                for (int i = 0; i < known; i++) {
                    try {
                        if (i > GENESIS_NODE_COUNT - 1 && respawnStaggerMs > 0) {
                            Thread.sleep(respawnStaggerMs);
                        }
                        spawnNode(i, config);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    } catch (Exception e) {
                        System.err.println("[Orchestrator] Failed to respawn node " + i + ": " + e.getMessage());
                    }
                }
                System.out.println("[Orchestrator] Respawn complete (" + known + " node(s)).");
            }, "node-respawner");
            respawner.start();
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/api/orchestrator/spawn_node", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if ("POST".equals(exchange.getRequestMethod())) {
                    try {
                        int nodeIndex = nextNodeIndex.getAndIncrement();
                        persistIndex(indexFile, nextNodeIndex.get());

                        Wallet wallet = spawnNode(nodeIndex, config);

                        // Return JSON response to the backend
                        String jsonResponse = String.format(
                            "{\n  \"status\": \"success\",\n  \"nodeIndex\": %d,\n  \"port\": %d,\n  \"publicKey\": \"%s\",\n  \"address\": \"%s\"\n}",
                            nodeIndex, 9000 + nodeIndex, wallet.getPublicKeyBase64(), wallet.getAddress()
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
            byte[] body = ("{\"status\":\"ok\",\"nextNodeIndex\":" + nextNodeIndex.get()
                    + ",\"chainEpoch\":" + chainEpoch.get() + "}")
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
            + " | mode=" + config.launchMode()
            + " | totalNodes=" + config.totalNodes()
            + " | nextNodeIndex=" + nextNodeIndex.get()
            + " | mempool=" + config.mempoolApiUrl()
            + " | eventBus=" + config.eventBusUrl());
    }

    /**
     * Derives the node's deterministic identity and launches it (process or
     * container). Used both for fresh spawns (HTTP endpoint) and for the
     * respawn-on-startup loop; identity derivation is deterministic, so
     * respawning yields exactly the same keys as the original spawn.
     */
    private static Wallet spawnNode(int nodeIndex, Config config) throws IOException, InterruptedException {
        // 1. Generate identity deterministically exactly like Node.java does
        Wallet wallet = Wallet.fromSeedPhrase("GENESIS_NODE_" + (nodeIndex + 1));
        int nodePort = 9000 + nodeIndex;

        // 2. Spawn the node independently (process or docker container)
        List<String> command = new ArrayList<>();
        if (config.dockerMode()) {
            // A stopped leftover container would block `docker run --name`;
            // remove it first (the node's chain state lives on the volume,
            // not in the container, so nothing is lost).
            new ProcessBuilder("docker", "rm", "-f", "node-" + nodeIndex)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start()
                    .waitFor();

            // Hard memory cap per node. Uncapped, each node JVM sizes its heap
            // off the whole Docker VM (~25%) and 16 nodes alone ate ~7GB, so
            // the OOM killer started shooting the backend and RabbitMQ.
            // UNIFORM for every node (validator-ness is dynamic — never tier
            // by index); override with NODE_MEMORY_LIMIT / NODE_HEAP env.
            String memoryLimit = System.getenv().getOrDefault("NODE_MEMORY_LIMIT", "384m");
            String nodeHeap    = System.getenv().getOrDefault("NODE_HEAP", "256m");

            command.addAll(List.of(
                "docker", "run", "-d",
                "--name", "node-" + nodeIndex,
                "--hostname", "node-" + nodeIndex,
                "--network", config.dockerNetwork(),
                "--memory", memoryLimit,
                // If a node is OOM-killed or crashes anyway, bring it back —
                // it resumes from its DB and the CatchupService re-syncs it.
                "--restart", "on-failure",
                "-e", "NODE_HOST_PATTERN=node-%d",
                "-e", "NODE_HEAP=" + nodeHeap,
                "-v", config.nodesVolume() + ":/app/nodes",
                config.nodeImage(),
                String.valueOf(nodeIndex), String.valueOf(config.totalNodes()),
                config.mempoolApiUrl(), config.eventBusUrl()
            ));
        } else {
            command.addAll(List.of(
                "java", "-cp", "target/blockchain.jar",
                "app.Main", String.valueOf(nodeIndex), String.valueOf(config.totalNodes()),
                config.mempoolApiUrl(), config.eventBusUrl()
            ));
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        pb.redirectOutput(new File("node_" + nodeIndex + "_orchestrated.log"));
        Process process = pb.start();
        System.out.println("[Orchestrator] Spawned Node " + nodeIndex
            + " on port " + nodePort
            + " (mode=" + config.launchMode() + ", PID " + process.pid() + ")");
        return wallet;
    }

    /** Deletes node chain databases (node_*.db + SQLite -wal/-shm sidecars). */
    private static int wipeChainState(Path nodesDir) {
        int wiped = 0;
        try (var files = Files.newDirectoryStream(nodesDir, "node_*.db*")) {
            for (Path file : files) {
                Files.deleteIfExists(file);
                wiped++;
            }
        } catch (IOException e) {
            System.err.println("[Orchestrator] Failed to wipe chain state: " + e.getMessage());
        }
        return wiped;
    }

    private static long bumpEpoch(Path epochFile) {
        long next = loadEpoch(epochFile) + 1;
        try {
            Files.writeString(epochFile, String.valueOf(next));
        } catch (IOException e) {
            System.err.println("[Orchestrator] Could not persist chain epoch: " + e.getMessage());
        }
        return next;
    }

    private static long loadEpoch(Path epochFile) {
        try {
            if (Files.exists(epochFile)) {
                return Long.parseLong(Files.readString(epochFile).trim());
            }
        } catch (Exception e) {
            System.err.println("[Orchestrator] Could not read chain epoch, assuming 0: " + e.getMessage());
        }
        return 0;
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
