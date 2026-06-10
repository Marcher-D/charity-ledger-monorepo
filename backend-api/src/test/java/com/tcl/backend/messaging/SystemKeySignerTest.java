package com.tcl.backend.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.junit.jupiter.api.Test;

/*
 * Pins the system signer to the on-chain canonical transaction format.
 *
 * The blockchain verifies DEPOSIT transactions by rebuilding
 *   type|senderPubKey|receiverPubKey|amount(%.2f)|firstValid|lastValid|timestamp|note
 * computing txId = SHA-256(signable), and checking the Ed25519 signature over
 * the signable UTF-8 bytes against the system public key. This test rebuilds
 * that exact pipeline independently; if SystemKeySigner ever drifts from the
 * chain's Transaction.getSignableData()/Ed25519Util semantics, it fails.
 */
class SystemKeySignerTest {

    private final SystemKeySigner signer = new SystemKeySigner();

    @Test
    void depositMatchesOnChainCanonicalFormatAndVerifies() throws Exception {
        String receiverPubKey = "lhAIU6xXcADyNDudvrOJQ5gZnyv9gXYNB6Sfw1Gp2cY=";
        long timestamp = 1_718_029_000_000L;

        OnChainTransactionMessage message = signer.signSystemTransaction(
                "DEPOSIT", receiverPubKey, new java.math.BigDecimal("100.50"),
                0L, 999_999_999L, timestamp, "faucet");

        // Canonical signable string rebuilt exactly like the on-chain Transaction class.
        String signable = "DEPOSIT|" + signer.publicKey() + "|" + receiverPubKey
                + "|100.50|0|999999999|" + timestamp + "|faucet";

        // txId = SHA-256(signable) in lowercase hex.
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        StringBuilder expectedTxId = new StringBuilder();
        for (byte b : digest.digest(signable.getBytes(StandardCharsets.UTF_8))) {
            expectedTxId.append(String.format("%02x", b));
        }
        assertThat(message.txId()).isEqualTo(expectedTxId.toString());

        // The signature must verify against the system public key over the signable bytes.
        Ed25519PublicKeyParameters publicKey =
                new Ed25519PublicKeyParameters(Base64.getDecoder().decode(signer.publicKey()), 0);
        Ed25519Signer verifier = new Ed25519Signer();
        verifier.init(false, publicKey);
        byte[] data = signable.getBytes(StandardCharsets.UTF_8);
        verifier.update(data, 0, data.length);
        assertThat(verifier.verifySignature(Base64.getDecoder().decode(message.ed25519Signature()))).isTrue();

        // Echoed message fields are the signed values.
        assertThat(message.type()).isEqualTo("DEPOSIT");
        assertThat(message.senderPubKey()).isEqualTo(signer.publicKey());
        assertThat(message.receiverPubKey()).isEqualTo(receiverPubKey);
        assertThat(message.amount()).isEqualTo(100.50);
        assertThat(message.note()).isEqualTo("faucet");
    }

    @Test
    void systemAddressIsFirst40HexOfSha256OverPublicKeyBytes() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(Base64.getDecoder().decode(signer.publicKey()));
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }
        assertThat(signer.address()).isEqualTo(hex.substring(0, 40));
    }
}
