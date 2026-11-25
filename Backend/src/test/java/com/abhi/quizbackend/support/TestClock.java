package com.abhi.quizbackend.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock a test can set and move forward. */
public class TestClock extends Clock {

    private Instant now;

    public TestClock(Instant start) {
        this.now = start;
    }

    public void set(Instant instant) {
        now = instant;
    }

    public void plus(Duration duration) {
        now = now.plus(duration);
    }

    public void plusDays(int days) {
        plus(Duration.ofDays(days));
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        // Reads the current instant on every call, so later moves still count.
        TestClock self = this;
        return new Clock() {
            @Override public ZoneId getZone() { return zone; }
            @Override public Clock withZone(ZoneId other) { return self.withZone(other); }
            @Override public Instant instant() { return self.instant(); }
        };
    }

    @Override
    public Instant instant() {
        return now;
    }
}
