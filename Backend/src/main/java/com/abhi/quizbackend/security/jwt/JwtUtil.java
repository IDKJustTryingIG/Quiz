package com.abhi.quizbackend.security.jwt;

import com.abhi.quizbackend.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {

    public static final String ROLE_CLAIM = "role";
    public static final String EMAIL_CLAIM = "email";
    public static final String USERNAME_CLAIM = "username";

    /** HS256 needs a 256-bit key, so the raw secret must be at least 32 characters. */
    private static final int MIN_SECRET_LENGTH = 32;

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.issuer}")
    private String issuer;

    @Value("${jwt.expiration}")
    private long expiration;

    private SecretKey signingKey;
    private JwtParser parser;

    /**
     * Fail at startup rather than on the first login: a blank or short secret is a
     * deployment mistake, and Keys.hmacShaKeyFor would otherwise throw per request.
     */
    @PostConstruct
    void init() {

        if (secret == null || secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "jwt.secret must be at least " + MIN_SECRET_LENGTH
                            + " characters (set JWT_SECRET; generate with: openssl rand -base64 48)");
        }

        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

        // Parsers built this way are immutable and thread-safe, so one is reused.
        this.parser = Jwts.parser()
                .verifyWith(signingKey)
                // Rejects a structurally valid token minted by some other system.
                .requireIssuer(issuer)
                // Tolerates small clock differences between issuer and verifier.
                .clockSkewSeconds(60)
                .build();
    }

    /**
     * Access token. Subject is the user id, which is stable; the email can change.
     */
    public String generateAccessToken(User user) {

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(user.getUserId()))
                // Unique token id, so individual tokens can be denylisted if needed.
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiryDate)
                .claim(EMAIL_CLAIM, user.getEmail())
                .claim(USERNAME_CLAIM, user.getUsername())
                // Stored without the ROLE_ prefix: that is a Spring detail, not an API one.
                .claim(ROLE_CLAIM, user.getRole().name())
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Verifies signature, issuer and expiry.
     *
     * @throws io.jsonwebtoken.JwtException if the token is invalid in any way.
     */
    public Claims extractClaims(String token) {
        return parser.parseSignedClaims(token).getPayload();
    }

    public Long extractUserId(String token) {
        return Long.valueOf(extractClaims(token).getSubject());
    }

    public String extractEmail(String token) {
        return extractClaims(token).get(EMAIL_CLAIM, String.class);
    }

    public String extractRole(String token) {
        return extractClaims(token).get(ROLE_CLAIM, String.class);
    }

    public long getAccessTokenTtlSeconds() {
        return expiration / 1000;
    }
}
