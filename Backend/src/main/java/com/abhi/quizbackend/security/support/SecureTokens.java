package com.abhi.quizbackend.security.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Opaque token generation and hashing.
 *
 * <p>Refresh tokens and exchange codes are hashed with SHA-256 rather than BCrypt.
 * BCrypt is deliberately slow to protect <em>low-entropy</em> secrets like passwords
 * from offline brute force; these values are 256 bits of randomness, so there is
 * nothing to brute force. More practically, BCrypt salts every hash, which makes the
 * column unindexable and would turn every refresh into a full table scan. SHA-256
 * gives a unique indexed column and an O(1) lookup, while a database leak still
 * yields no usable token.
 */
public final class SecureTokens
{
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final int TOKEN_BYTES = 32;

    private SecureTokens()
    {
    }

    /** 256 bits of randomness, URL-safe so it survives cookies and query strings. */
    public static String newToken()
    {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    public static String sha256Hex(String raw)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException ex)
        {
            // SHA-256 is required of every JVM; this cannot happen.
            throw new IllegalStateException("SHA-256 unavailable",ex);
        }
    }
}