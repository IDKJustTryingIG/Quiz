package com.abhi.quizbackend.service;

import com.abhi.quizbackend.entity.User;
import com.abhi.quizbackend.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Revocation that must outlive a failing request.
 *
 * <p>Reuse detection revokes every session and then rejects the request by throwing.
 * Done inside the caller's transaction, Spring's rollback-on-RuntimeException would
 * discard the revoke along with the exception, so the theft response would log a
 * warning and change nothing.
 *
 * <p>A separate bean is required, not merely a separate method: {@code @Transactional}
 * is applied by a proxy, and a self-invocation inside {@code RefreshTokenService} would
 * bypass it and silently reinstate the bug.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenRevoker
{
    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    /**
     * Commits independently of the caller, so the revoke survives the rejection that
     * follows it.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int revokeAllForUser(User user)
    {
        return refreshTokenRepository.revokeAllForUser(user,clock.instant());
    }
}
