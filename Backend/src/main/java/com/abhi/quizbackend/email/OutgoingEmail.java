package com.abhi.quizbackend.email;

import java.util.Map;

/**
 * One email, ready to send.
 *
 * @param idempotencyKey the same key for every try of the same email, so the
 *                       provider sends it once even if a reply is lost
 * @param headers        extra headers, such as List-Unsubscribe
 */
public record OutgoingEmail(
        String to,
        String subject,
        String html,
        String text,
        String idempotencyKey,
        Map<String, String> headers
) {
}
