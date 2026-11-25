package com.abhi.quizbackend.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** The daily email, bound from app.email.* in application.properties. */
@ConfigurationProperties(prefix = "app.email")
@Getter
@Setter
public class EmailProperties {

    /** The Resend API key. Without one, emails wait in the queue and nothing is sent. */
    private String resendApiKey = "";

    /**
     * The sender. onboarding@resend.dev is Resend's test sender: it delivers
     * only to the email address of the Resend account. Use an address on a
     * verified domain to send to every user.
     */
    private String from = "Quiz <onboarding@resend.dev>";

    /** The frontend, for the links in the email. */
    private String appUrl = "http://localhost:5173";

    /** This API, for the one-click unsubscribe header that mail apps call. */
    private String apiUrl = "http://localhost:8080";

    /**
     * The pause between two sends. Resend allows a few requests per second
     * by default. Check the limit of your plan.
     */
    private Duration sendInterval = Duration.ofMillis(600);

    /** Sends per email before it is FAILED. Only temporary errors are tried again. */
    private int maxAttempts = 5;

    /** The delay after the first temporary failure. It doubles after each later one. */
    private Duration retryDelay = Duration.ofMinutes(1);
}
