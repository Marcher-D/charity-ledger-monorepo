package com.tcl.backend.messaging;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Locale;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.springframework.stereotype.Service;

/*
 * Signs system transactions (DEPOSIT) with the chain's system key.
 *
 * The chain only accepts DEPOSIT transactions signed by the holder of the key
 * derived from the public seed phrase "SYSTEM_DEPOSIT_KEY_V1" (see the on-chain
 * GenesisConfig). The backend is the system operator, so it derives the same
 * key pair and signs deposits locally. User-node keys are NEVER derived here —
 * users' transactions are signed by their own nodes (NodeSignerClient).
 *
 * Everything below mirrors the on-chain code exactly:
 *  - seed bytes  = parse 32 bytes from sha256Hex("SYSTEM_DEPOSIT_KEY_V1")     (GenesisConfig)
 *  - key pair    = Ed25519PrivateKeyParameters(seed) + generatePublicKey()    (Ed25519Util)
 *  - signable    = type|sender|receiver|%.2f amount|firstValid|lastValid|ts|note (Transaction)
 *  - txId        = sha256Hex(signable)                                        (Transaction)
 *  - signature   = Base64(Ed25519(signable as UTF-8 bytes))                   (Ed25519Util)
 */
@Service
public class SystemKeySigner {

    private static final String SYSTEM_SEED_PHRASE = "SYSTEM_DEPOSIT_KEY_V1";

    private final Ed25519PrivateKeyParameters privateKey;
    private final String publicKeyBase64;
    private final String address;

    public SystemKeySigner() {
        byte[] seed = hexToBytes(sha256Hex(SYSTEM_SEED_PHRASE), 32);
        this.privateKey = new Ed25519PrivateKeyParameters(seed, 0);
        byte[] publicKeyBytes = privateKey.generatePublicKey().getEncoded();
        this.publicKeyBase64 = Base64.getEncoder().encodeToString(publicKeyBytes);
        // On-chain address = first 40 hex chars of SHA-256 over the raw public key bytes.
        this.address = sha256Hex(publicKeyBytes).substring(0, 40);
    }

    public String publicKey() {
        return publicKeyBase64;
    }

    public String address() {
        return address;
    }

    /*
     * Builds and signs a complete on-chain transaction with the system key as
     * sender. Used for DEPOSIT (system -> user).
     */
    public OnChainTransactionMessage signSystemTransaction(
            String type,
            String receiverPubKey,
            BigDecimal amount,
            long firstValid,
            long lastValid,
            long timestamp,
            String note
    ) {
        double amountValue = amount.doubleValue();
        String signable = type + "|" + publicKeyBase64 + "|" +
                (receiverPubKey != null ? receiverPubKey : "") + "|" +
                String.format(Locale.US, "%.2f", amountValue) + "|" +
                firstValid + "|" + lastValid + "|" + timestamp + "|" +
                (note != null ? note : "");

        String txId = sha256Hex(signable);
        Ed25519Signer signer = new Ed25519Signer();
        signer.init(true, privateKey);
        byte[] message = signable.getBytes(StandardCharsets.UTF_8);
        signer.update(message, 0, message.length);
        String signature = Base64.getEncoder().encodeToString(signer.generateSignature());

        return new OnChainTransactionMessage(
                txId, type, publicKeyBase64, receiverPubKey,
                amountValue, firstValid, lastValid, timestamp, note, signature
        );
    }

    private static String sha256Hex(String data) {
        return sha256Hex(data.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 failed", e);
        }
    }

    private static byte[] hexToBytes(String hex, int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }
}
