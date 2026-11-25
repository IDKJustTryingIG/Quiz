package com.abhi.quizbackend.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** The queue of quizzes to make, bound from app.quiz.jobs.* in application.properties. */
@ConfigurationProperties(prefix = "app.quiz.jobs")
@Getter
@Setter
public class QuizJobProperties {

    /**
     * Model calls at the same time. Each takes up to a minute. Raise it only
     * as far as the Gemini rate limit of your plan allows.
     */
    private int workers = 3;

    /** Model calls per job before it is FAILED. */
    private int maxAttempts = 4;

    /** The delay after the first failure. It doubles after each later failure. */
    private Duration retryDelay = Duration.ofMinutes(1);

    /** A RUNNING job older than this lost its worker, and goes back in the queue. */
    private Duration staleAfter = Duration.ofMinutes(10);
}
