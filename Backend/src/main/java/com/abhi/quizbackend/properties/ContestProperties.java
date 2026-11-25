package com.abhi.quizbackend.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.time.LocalTime;

/**
 * The times of the daily contest, bound from app.contest.* in
 * application.properties, in the zone of app.schedule.zone.
 *
 * The open and close crons in application.properties must match these times.
 */
@ConfigurationProperties(prefix = "app.contest")
@Getter
@Setter
public class ContestProperties {

    /** When the contest opens. */
    private LocalTime opensAt = LocalTime.of(18, 0);

    /** How long after the opening a user can still start. */
    private Duration startWindow = Duration.ofMinutes(5);

    /** From the opening to the close. Correct answers are shown after the close. */
    private Duration length = Duration.ofMinutes(15);

    /** The time limit of one attempt. An attempt never runs past the close. */
    private Duration attemptTime = Duration.ofMinutes(10);
}
