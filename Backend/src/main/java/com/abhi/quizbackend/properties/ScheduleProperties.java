package com.abhi.quizbackend.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;

/** The calendar of the daily quiz, bound from app.schedule.* in application.properties. */
@ConfigurationProperties(prefix = "app.schedule")
@Getter
@Setter
public class ScheduleProperties {

    /**
     * The one time zone for every user. "Today", the day number and the
     * times of the daily jobs all use it.
     */
    private ZoneId zone = ZoneId.of("Asia/Kolkata");
}
