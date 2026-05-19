package com.tcl.backend.messaging.mempool;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

/**
 * Lớp CryptoCore - "Trái tim" mật mã của Blockchain.
 * Chứa các hàm về Băm (Hash), Chữ ký (ECDSA) và Xổ số ngẫu nhiên (VRF).
 */
public class CryptoCore {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    // =========================================================================
    // 1. SHA-256
    // =========================================================================
    public static String applySha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi băm SHA-256", e);
        }
    }

    // =========================================================================
    // 2. ECDSA Key Generation
    // =========================================================================
    public static KeyPair generateKeyPair() {
        try {
            ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256k1");
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("ECDSA", "BC");
            SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
            keyGen.initialize(ecSpec, random);
            return keyGen.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi tạo cặp khóa ECDSA", e);
        }
    }

    // =========================================================================
    // 3. Sign & Verify
    // =========================================================================

    /**
     * Ký dữ liệu bằng Private Key.
     * Dùng SHA256withDETECDSA (Deterministic ECDSA - BouncyCastle) để đảm bảo
     * cùng 1 input luôn cho cùng 1 signature -> cần thiết cho VRF.
     */
    public static String signData(PrivateKey privateKey, String data) {
        try {
            Signature ecdsaSign = Signature.getInstance("SHA256withDETECDSA", "BC");
            ecdsaSign.initSign(privateKey);
            ecdsaSign.update(data.getBytes(StandardCharsets.UTF_8));
            byte[] signature = ecdsaSign.sign();
            return Base64.getEncoder().encodeToString(signature);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi ký dữ liệu", e);
        }
    }

    /**
     * Xác minh chữ ký bằng Public Key.
     * Dùng SHA256withECDSA (standard) vì verify không cần deterministic.
     */
    public static boolean verifySignature(PublicKey publicKey, String data, String signature) {
        try {
            Signature ecdsaVerify = Signature.getInstance("SHA256withECDSA", "BC");
            ecdsaVerify.initVerify(publicKey);
            ecdsaVerify.update(data.getBytes(StandardCharsets.UTF_8));
            byte[] sigBytes = Base64.getDecoder().decode(signature);
            return ecdsaVerify.verify(sigBytes);
        } catch (Exception e) {
            return false;
        }
    }

    // =========================================================================
    // 4. VRF (Pseudo-VRF dùng Deterministic ECDSA)
    // =========================================================================
    public static String generateVRFProof(PrivateKey privateKey, String seed) {
        return signData(privateKey, "VRF_SEED:" + seed);
    }

    public static boolean verifyVRF(PublicKey publicKey, String seed, String vrfProof) {
        return verifySignature(publicKey, "VRF_SEED:" + seed, vrfProof);
    }

    /**
     * Chuyển VRF Proof thành số nguyên để so sánh điểm xổ số.
     */
    public static long vrfProofToNumber(String vrfProof) {
        String hash = applySha256(vrfProof);
        return Long.parseLong(hash.substring(0, 8), 16);
    }

    // =========================================================================
    // 5. Key Serialization Helpers
    // =========================================================================

    /** Chuyển PublicKey thành chuỗi Base64 để lưu vào DB / JSON */
    public static String encodePublicKey(PublicKey publicKey) {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    /** Khôi phục PublicKey từ chuỗi Base64 */
    public static PublicKey decodePublicKey(String base64Key) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(base64Key);
            java.security.spec.X509EncodedKeySpec spec = new java.security.spec.X509EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("ECDSA", "BC");
            return kf.generatePublic(spec);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi decode Public Key", e);
        }
    }
}