package com.abhi.quizbackend.service;

import com.abhi.quizbackend.entity.RefreshToken;
import com.abhi.quizbackend.entity.User;
import com.abhi.quizbackend.exception.InvalidRefreshTokenException;
import com.abhi.quizbackend.repository.RefreshTokenRepository;
import com.abhi.quizbackend.security.support.SecureTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest
{
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final long TTL_MS = Duration.ofDays(30).toMillis();

    @Mock
    private RefreshTokenRepository repository;

    @Mock
    private RefreshTokenRevoker revoker;

    private RefreshTokenService service;
    private User user;

    @BeforeEach
    void setUp()
    {
        service = new RefreshTokenService(repository,revoker,Clock.fixed(NOW,ZoneOffset.UTC));
        ReflectionTestUtils.setField(service,"refreshExpirationMs",TTL_MS);

        user = new User();
        user.setUserId(7L);
        user.setEmail("abhi@example.com");
    }

    @Test
    void storesOnlyTheHashNeverTheRawToken()
    {
        String raw = service.issue(user);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(saved.capture());

        RefreshToken stored = saved.getValue();
        assertThat(stored.getTokenHash()).isEqualTo(SecureTokens.sha256Hex(raw));
        // The raw value must appear nowhere on the persisted row.
        assertThat(stored.getTokenHash()).isNotEqualTo(raw);
        assertThat(stored.getExpiresAt()).isEqualTo(NOW.plusMillis(TTL_MS));
        assertThat(stored.getRevokedAt()).isNull();
    }

    @Test
    void issuesADifferentTokenEveryTime()
    {
        assertThat(service.issue(user))
                .isNotEqualTo(service.issue(user));
    }

    @Test
    void rotationRevokesTheOldTokenAndIssuesANewOne()
    {
        String raw = "original-token";
        RefreshToken existing = usableToken(raw);
        given(repository.findByTokenHash(SecureTokens.sha256Hex(raw))).willReturn(Optional.of(existing));

        RefreshTokenService.Issued issued = service.rotate(raw);

        assertThat(issued.rawToken()).isNotEqualTo(raw);
        assertThat(issued.user()).isSameAs(user);
        // The presented token is dead the moment it is exchanged.
        assertThat(existing.getRevokedAt()).isEqualTo(NOW);
        verify(repository).save(any(RefreshToken.class));
    }

    @Test
    void replayingARotatedTokenRevokesEverySessionForThatUser()
    {
        String raw = "stolen-token";
        RefreshToken revoked = usableToken(raw);
        revoked.setRevokedAt(NOW.minusSeconds(60));
        // Set by rotation: this token was already exchanged for a successor.
        revoked.setReplacedById(99L);
        given(repository.findByTokenHash(SecureTokens.sha256Hex(raw))).willReturn(Optional.of(revoked));

        assertThatThrownBy(() -> service.rotate(raw))
                .isInstanceOf(InvalidRefreshTokenException.class);

        // Spending an already-spent token is the signal that one of them is stolen.
        verify(revoker).revokeAllForUser(user);
        verify(repository,never()).save(any(RefreshToken.class));
    }

    @Test
    void replayingASupersededTokenDoesNotKillTheNewSession()
    {
        String raw = "displaced-device-token";
        RefreshToken superseded = usableToken(raw);
        superseded.setRevokedAt(NOW.minusSeconds(60));
        // No successor: revoked by a newer login or by logout, not by rotation.
        superseded.setReplacedById(null);
        given(repository.findByTokenHash(SecureTokens.sha256Hex(raw))).willReturn(Optional.of(superseded));

        assertThatThrownBy(() -> service.rotate(raw))
                .isInstanceOf(InvalidRefreshTokenException.class);

        // The displaced device refreshing must not sign the user out of the device they
        // just logged into -- the whole hazard introduced by single-device login.
        verify(revoker,never()).revokeAllForUser(any());
    }

    @Test
    void issueExclusiveRevokesExistingSessionsFirst()
    {
        given(repository.revokeAllForUser(user,NOW)).willReturn(2);

        String raw = service.issueExclusive(user);

        assertThat(raw).isNotBlank();

        InOrder inOrder = inOrder(repository);
        // Order matters: revoking after saving would revoke the token just issued.
        inOrder.verify(repository).revokeAllForUser(user,NOW);
        inOrder.verify(repository).save(any(RefreshToken.class));
        // Same transaction as the issue: a failed login must not end the existing session.
        verify(revoker,never()).revokeAllForUser(any());
    }

    @Test
    void rotationDoesNotRevokeOtherSessions()
    {
        String raw = "live-token";
        RefreshToken existing = usableToken(raw);
        given(repository.findByTokenHash(SecureTokens.sha256Hex(raw))).willReturn(Optional.of(existing));

        service.rotate(raw);

        // Refreshing continues a session; it is not a new login.
        verify(revoker,never()).revokeAllForUser(any());
    }

    @Test
    void rejectsExpiredToken()
    {
        String raw = "old-token";
        RefreshToken expired = usableToken(raw);
        expired.setExpiresAt(NOW.minusSeconds(1));
        given(repository.findByTokenHash(SecureTokens.sha256Hex(raw))).willReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.rotate(raw))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessageContaining("expired");

        // An expired token is not evidence of theft, so other sessions survive.
        verify(revoker,never()).revokeAllForUser(any());
    }

    @Test
    void rejectsUnknownAndMissingTokens()
    {
        given(repository.findByTokenHash(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("nonsense"))
                .isInstanceOf(InvalidRefreshTokenException.class);
        assertThatThrownBy(() -> service.rotate(null))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void revokeIsSilentForUnknownTokens()
    {
        given(repository.findByTokenHash(any())).willReturn(Optional.empty());

        // Logout must not reveal whether a session existed.
        assertThatNoException().isThrownBy(() -> service.revoke("whatever"));
    }

    @Test
    void revokeMarksTheTokenRevoked()
    {
        String raw = "live-token";
        RefreshToken token = usableToken(raw);
        given(repository.findByTokenHash(SecureTokens.sha256Hex(raw))).willReturn(Optional.of(token));

        service.revoke(raw);

        assertThat(token.getRevokedAt()).isEqualTo(NOW);
    }

    @Test
    void reuseRevocationCommitsIndependentlyOfTheFailingRequest() throws Exception
    {
        // rotate() throws straight after revoking, and a RuntimeException rolls the
        // caller's transaction back. Without REQUIRES_NEW the revoke is discarded and
        // theft detection silently does nothing -- which no mock-based test can see.
        Transactional tx = RefreshTokenRevoker.class
                .getMethod("revokeAllForUser",com.abhi.quizbackend.entity.User.class)
                .getAnnotation(Transactional.class);

        assertThat(tx).isNotNull();
        assertThat(tx.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    private RefreshToken usableToken(String raw)
    {
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(SecureTokens.sha256Hex(raw));
        token.setIssuedAt(NOW.minusSeconds(3600));
        token.setExpiresAt(NOW.plusMillis(TTL_MS));
        return token;
    }
}
