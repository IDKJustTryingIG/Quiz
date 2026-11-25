package com.abhi.quizbackend.models;

import com.abhi.quizbackend.entity.EmailStatus;

import java.time.Instant;
import java.time.LocalDate;

/** One daily email, as the admin page shows it. No email address: the username is enough. */
public record EmailDeliveryResponse(
        Long deliveryId,
        LocalDate sendDate,
        String username,
        String trackName,
        EmailStatus status,
        int attempts,
        Instant nextAttemptAt,
        String lastError,
        Instant sentAt
) {
}
