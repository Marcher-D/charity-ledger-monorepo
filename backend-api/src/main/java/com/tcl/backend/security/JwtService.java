package com.tcl.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

// Service responsible for creating and validating JWT access tokens.
// JWTs let the frontend prove "I already logged in" on later API requests.
@Service
public class JwtService {

    // Secret signing key used to create and verify token signatures.
    // If someone changes the token text, signature verification will fail.
    private final SecretKey key;

    // Token lifetime in seconds, read from application.yml.
    private final long expiresInSeconds;

    public JwtService(
            // @Value injects configuration values from application.yml/environment variables.
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expires-in-seconds}") long expiresInSeconds
    ) {
        // Convert the configured secret text into an HMAC key used by the JWT library.
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiresInSeconds = expiresInSeconds;
    }

    // Create a signed JWT for a successfully authenticated account.
    public String generateToken(AuthenticatedAccount account) {
        Instant now = Instant.now();
        return Jwts.builder()
                // A JWT has three final parts: header.payload.signature.
                // This builder section fills the payload, which is the token body.
                // subject is the main identity in the token. Here it is the login email.
                .subject(account.getUsername())
                // Extra token data. These are called JWT claims.
                // They become JSON fields inside the payload.
                .claim("accountId", account.getId().toString())
                .claim("role", account.getAccount().getRole().name())
                // Time the token was created.
                .issuedAt(Date.from(now))
                // Time the token becomes invalid.
                .expiration(Date.from(now.plusSeconds(expiresInSeconds)))
                // Sign the token so the backend can detect tampering later.
                // The JWT library creates the header metadata, including the signing algorithm.
                .signWith(key)
                // Build the final compact JWT string: header.payload.signature.
                // compact() is the step that turns the builder into the actual String sent to frontend.
                .compact();
    }

    // Read the token subject without exposing parsing details to the rest of the app.
    public String extractSubject(String token) {
        return claims(token).getSubject();
    }

    // Check that the token belongs to this user and has not expired.
    public boolean isValid(String token, UserDetails userDetails) {
        String subject = extractSubject(token);
        return subject.equals(userDetails.getUsername()) && claims(token).getExpiration().after(new Date());
    }

    // Used in the login/register response so the frontend knows token lifetime.
    public long getExpiresInSeconds() {
        return expiresInSeconds;
    }

    // Parse and verify the signed JWT, then return its claims/body.
    // verifyWith(key) means the same secret key must have signed this token.
    private Claims claims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
