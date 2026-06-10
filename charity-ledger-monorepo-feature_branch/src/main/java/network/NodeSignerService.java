package network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import app.Wallet;
import crypto.Ed25519Util;
import model.Transaction;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * NodeSignerService — Per-node transaction signing endpoint.
 *
 * Runs alongside app.Main inside each node container. It holds the node's own
 * deterministic wallet and signs transactions on behalf of the node's owner so
 * the off-chain backend NEVER holds or sees a node private key. The backend
 * sends the canonical transaction fields; this service builds a model.Transaction
 * (the single source of truth for canonical signable data), computes the txId,
 * signs it, and returns the signature.
 *
 * Endpoints:
 *   POST /api/signer/sign
 *     Request : {"type":"DONATE","receiverPubKey":"base64|null","amount":25.0,
 *                "firstValid":0,"lastValid":999999999,"timestamp":1718029000000,
 *                "note":"optional"}
 *     Response: {"txId":"...","senderPubKey":"base64","address":"40-hex",
 *                "ed25519Signature":"base64","type":"DONATE","receiverPubKey":"...",
 *                "amount":25.0,"firstValid":0,"lastValid":999999999,
 *                "timestamp":1718029000000,"note":"optional"}
 *   GET  /api/signer/health
 *     Response: {"status":"ok","nodeIndex":N,"address":"...","publicKey":"..."}
 *
 * SECURITY NOTE: this service signs anything it is asked to sign. It must only
 * ever be reachable from inside the trusted Docker network (it is never published
 * to the host). A production system would authenticate the caller and enforce
 * per-owner policy here.
 *
 * Usage: java -cp blockchain.jar network.NodeSignerService <nodeIndex> [port]
 */
public class NodeSignerService {

    private static final Gson GSON = new Gson();
    private static final int DEFAULT_PORT = 7100;

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java -cp blockchain.jar network.NodeSignerService <nodeIndex> [port]");
            System.exit(1);
        }
        int nodeIndex = Integer.parseInt(args[0]);
        int port = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;

        // Identical derivation to Node.java / NodeOrchestrator — same identity as the node.
        Wallet wallet = Wallet.fromSeedPhrase("GENESIS_NODE_" + (nodeIndex + 1));

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/api/signer/sign", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            try (InputStream is = exchange.getRequestBody()) {
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                JsonObject req = GSON.fromJson(body, JsonObject.class);

                Transaction tx = new Transaction();
                tx.setType(Transaction.Type.valueOf(req.get("type").getAsString()));
                tx.setSenderPubKey(wallet.getPublicKeyBase64());
                tx.setReceiverPubKey(req.has("receiverPubKey") && !req.get("receiverPubKey").isJsonNull()
                        ? req.get("receiverPubKey").getAsString() : null);
                tx.setAmount(req.get("amount").getAsDouble());
                tx.setFirstValid(req.has("firstValid") ? req.get("firstValid").getAsLong() : 0L);
                tx.setLastValid(req.has("lastValid") ? req.get("lastValid").getAsLong() : 999_999_999L);
                tx.setTimestamp(req.has("timestamp") ? req.get("timestamp").getAsLong()
                        : System.currentTimeMillis());
                tx.setNote(req.has("note") && !req.get("note").isJsonNull()
                        ? req.get("note").getAsString() : null);

                String signable = tx.getSignableData();
                tx.setTxId(tx.computeTxId());
                byte[] sig = Ed25519Util.sign(wallet.getPrivateKey(),
                        signable.getBytes(StandardCharsets.UTF_8));
                tx.setEd25519Signature(Base64.getEncoder().encodeToString(sig));

                JsonObject resp = new JsonObject();
                resp.addProperty("txId", tx.getTxId());
                resp.addProperty("senderPubKey", wallet.getPublicKeyBase64());
                resp.addProperty("address", wallet.getAddress());
                resp.addProperty("ed25519Signature", tx.getEd25519Signature());
                resp.addProperty("type", tx.getType().name());
                if (tx.getReceiverPubKey() != null) resp.addProperty("receiverPubKey", tx.getReceiverPubKey());
                resp.addProperty("amount", tx.getAmount());
                resp.addProperty("firstValid", tx.getFirstValid());
                resp.addProperty("lastValid", tx.getLastValid());
                resp.addProperty("timestamp", tx.getTimestamp());
                if (tx.getNote() != null) resp.addProperty("note", tx.getNote());

                byte[] respBytes = GSON.toJson(resp).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, respBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(respBytes);
                }
                System.out.println("[NodeSigner " + nodeIndex + "] Signed " + tx.getType()
                        + " txId=" + tx.getTxId().substring(0, 8) + "...");
            } catch (Exception e) {
                String error = "{\"error\": \"" + e.getMessage() + "\"}";
                byte[] errBytes = error.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(400, errBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(errBytes);
                }
            }
        });

        server.createContext("/api/signer/health", exchange -> {
            JsonObject health = new JsonObject();
            health.addProperty("status", "ok");
            health.addProperty("nodeIndex", nodeIndex);
            health.addProperty("address", wallet.getAddress());
            health.addProperty("publicKey", wallet.getPublicKeyBase64());
            byte[] body = GSON.toJson(health).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        server.setExecutor(null);
        server.start();
        System.out.println("[NodeSigner " + nodeIndex + "] Signing service on port " + port
                + " | address=" + wallet.getAddress());
    }
}
