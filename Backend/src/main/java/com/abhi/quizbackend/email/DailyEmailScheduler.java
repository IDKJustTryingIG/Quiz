package com.abhi.quizbackend.email;

import com.abhi.quizbackend.properties.EmailProperties;
import com.abhi.quizbackend.service.EnrollmentService;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * At 06:00 it plans the day's emails. Every 30 seconds it sends the emails
 * that are due, which also covers retries and emails planned by hand.
 */
@Component
public class DailyEmailScheduler {

    private static final Logger log = LoggerFactory.getLogger(DailyEmailScheduler.class);

    private final DailyEmailService emailService;
    private final EnrollmentService enrollmentService;
    private final EmailProperties emailProperties;

    public DailyEmailScheduler(DailyEmailService emailService,
                               EnrollmentService enrollmentService,
                               EmailProperties emailProperties) {
        this.emailService = emailService;
        this.enrollmentService = enrollmentService;
        this.emailProperties = emailProperties;
    }

    @PostConstruct
    void warnWithoutKey() {
        if (emailProperties.getResendApiKey().isBlank()) {
            log.warn("RESEND_API_KEY is not set. Daily emails are planned but not sent.");
        }
    }

    @Scheduled(cron = "${app.email.plan-cron}", zone = "${app.schedule.zone}")
    public void plan() {
        DailyEmailService.PlanResult result = emailService.plan(enrollmentService.today());
        log.info("Planned daily emails for {}: {} new, {} already planned, {} turned off",
                result.date(), result.planned(), result.alreadyPlanned(), result.optedOut());
    }

    @Scheduled(fixedDelayString = "${app.email.poll-delay}", initialDelayString = "${app.email.poll-delay}")
    public void send() {
        int sent = emailService.sendDue();
        if (sent > 0) {
            log.info("Sent or skipped {} daily emails", sent);
        }
    }
}
