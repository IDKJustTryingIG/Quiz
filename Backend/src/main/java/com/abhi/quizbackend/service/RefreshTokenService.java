package com.abhi.quizbackend.service;

import com.abhi.quizbackend.entity.RefreshToken;
import com.abhi.quizbackend.entity.User;
import com.abhi.quizbackend.exception.InvalidRefreshTokenException;
import com.abhi.quizbackend.repository.RefreshTokenRepository;
import com.abhi.quizbackend.security.support.SecureTokens;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class RefreshTokenService
{
    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenRevoker refreshTokenRevoker;
    private final Clock clock;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpirationMs;

    /** The raw token is returned once, here, and never persisted or logged. */
    public record Issued(User user,String rawToken)
    {
    }

    /**
     * Single-device login: every existing session for this user is revoked before the
     * new one is created, so at most one refresh token is ever live per user.
     *
     * <p>Called only from the login paths. Rotation deliberately does not go through
     * here: refreshing continues an existing session rather than starting one.
     */
    @Transactional
    public String issueExclusive(User user)
    {
        // Stays in the caller's transaction on purpose, unlike reuse detection: if
        // issuing the new token fails, a failed login must not have ended the session
        // the user already had.
        int revoked = refreshTokenRepository.revokeAllForUser(user,clock.instant());

        if (revoked > 0)
        {
            log.info("Login for userId={} ended {} existing session(s)",user.getUserId(),revoked);
        }

        return issue(user);
    }

    @Transactional
    public String issue(User user)
    {
        Instant now = clock.instant();

        String raw = SecureTokens.newToken();

        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(SecureTokens.sha256Hex(raw));
        token.setIssuedAt(now);
        token.setExpiresAt(now.plusMillis(refreshExpirationMs));

        refreshTokenRepository.save(token);

        return raw;
    }

    /**
     * Rotation: the presented token is revoked and a fresh one issued.
     *
     * <p>A revoked token is only evidence of theft when it had already been rotated:
     * that means someone is spending a token which was exchanged for a successor. Then
     * every session is revoked, which is what makes a stolen token detectable rather
     * than silently usable for its full life.
     *
     * <p>A token revoked by logout, or superseded by a newer login, has no successor.
     * Replaying one of those is just a stale client, so it gets a plain 401. Treating it
     * as theft would be actively harmful under single-device login: the displaced device
     * refreshing would revoke the session the user had only just signed into.
     */
    @Transactional
    public Issued rotate(String rawToken)
    {
        RefreshToken existing = findOrThrow(rawToken);
        Instant now = clock.instant();

        if (existing.isRevoked())
        {
            // replacedById is set only by rotation, so its presence distinguishes a
            // genuine replay from a session that was simply ended.
            if (existing.getReplacedById() != null)
            {
                // REQUIRES_NEW: this commits on its own, because the throw below would
                // otherwise roll the revoke back and leave every session live.
                int revoked = refreshTokenRevoker.revokeAllForUser(existing.getUser());

                log.warn("Refresh token reuse detected for userId={}; revoked {} session(s)",
                        existing.getUser().getUserId(),revoked);
            }
            else
            {
                log.debug("Refresh presented for an ended session, userId={}",
                        existing.getUser().getUserId());
            }

            throw new InvalidRefreshTokenException("Refresh token is no longer valid");
        }

        if (existing.isExpiredAt(now))
        {
            throw new InvalidRefreshTokenException("Refresh token has expired");
        }

        User user = existing.getUser();

        String raw = SecureTokens.newToken();
        RefreshToken replacement = new RefreshToken();
        replacement.setUser(user);
        replacement.setTokenHash(SecureTokens.sha256Hex(raw));
        replacement.setIssuedAt(now);
        replacement.setExpiresAt(now.plusMillis(refreshExpirationMs));
        refreshTokenRepository.save(replacement);

        existing.setRevokedAt(now);
        existing.setReplacedById(replacement.getId());

        return new Issued(user,raw);
    }

    /** Logout. Unknown tokens are ignored: logout must not report whether one existed. */
    @Transactional
    public void revoke(String rawToken)
    {
        if (rawToken == null || rawToken.isBlank())
        {
            return;
        }

        refreshTokenRepository.findByTokenHash(SecureTokens.sha256Hex(rawToken))
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> token.setRevokedAt(clock.instant()));
    }

    @Transactional
    public int revokeAllForUser(User user)
    {
        return refreshTokenRevoker.revokeAllForUser(user);
    }

    /** Rows stay briefly past expiry so reuse detection still sees recently-used tokens. */
    @Transactional
    public int purgeExpired(Duration retention)
    {
        return refreshTokenRepository.deleteExpiredBefore(clock.instant().minus(retention));
    }

    private RefreshToken findOrThrow(String rawToken)
    {
        if (rawToken == null || rawToken.isBlank())
        {
            throw new InvalidRefreshTokenException("Refresh token is missing");
        }

        return refreshTokenRepository.findByTokenHash(SecureTokens.sha256Hex(rawToken))
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token is not valid"));
    }

}
