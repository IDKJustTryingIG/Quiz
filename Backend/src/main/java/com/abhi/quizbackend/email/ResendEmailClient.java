package com.abhi.quizbackend.email;

import com.abhi.quizbackend.properties.EmailProperties;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

/**
 * Sends email through the Resend REST API: POST https://api.resend.com/emails.
 *
 * The Idempotency-Key header makes Resend send an email once, even when the
 * reply is lost and the sender tries again.
 */
@Component
public class ResendEmailClient implements EmailClient {

    private final RestClient restClient;
    private final EmailProperties properties;

    public ResendEmailClient(EmailProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.resend.com")
                .build();
    }

    private record SendRequest(String from, List<String> to, String subject, String html, String text,
                               Map<String, String> headers) {
    }

    private record SendResponse(String id) {
    }

    @Override
    public String send(OutgoingEmail email) {
        try {
            SendResponse response = restClient.post()
                    .uri("/emails")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getResendApiKey())
                    .header("Idempotency-Key", email.idempotencyKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new SendRequest(properties.getFrom(), List.of(email.to()), email.subject(),
                            email.html(), email.text(), email.headers()))
                    .retrieve()
                    .body(SendResponse.class);
            return response == null ? null : response.id();
        } catch (RestClientResponseException e) {
            HttpStatusCode status = e.getStatusCode();
            boolean temporary = status.value() == 429 || status.is5xxServerError();
            // Resend's error body says why, for example "You can only send testing emails to your own email address".
            throw new EmailSendException("Resend " + status.value() + ": " + e.getResponseBodyAsString(), temporary, e);
        } catch (ResourceAccessException e) {
            throw new EmailSendException("Could not reach Resend: " + e.getMessage(), true, e);
        }
    }
}
