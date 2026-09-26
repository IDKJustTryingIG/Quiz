package com.abhi.quizbackend.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Expired rows are kept for a grace period rather than deleted on expiry, so that a
 * recently-expired token presented again is still recognised as reuse instead of
 * looking like an unknown token.
 */
@Component
@RequiredArgsConstructor
public class TokenCleanupJob
{
    private static final Logger log = LoggerFactory.getLogger(TokenCleanupJob.class);
    private static final Duration REFRESH_RETENTION = Duration.ofDays(7);

    private final RefreshTokenService refreshTokenService;

    @Scheduled(cron = "0 15 3 * * *")
    public void purge()
    {
        int tokens = refreshTokenService.purgeExpired(REFRESH_RETENTION);

        if (tokens > 0)
        {
            log.info("Purged {} expired refresh tokens",tokens);
        }
    }
}
