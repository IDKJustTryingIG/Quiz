package com.abhi.quizbackend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * The two fixed moments of the contest.
 *
 * - At the opening (18:00) it plans the day once more, which creates any
 *   missing contest and queues failed quizzes again, then freezes the quizzes.
 * - Just after the close (18:15) it scores the attempts nobody submitted.
 *
 * Both are also done when needed: a request after the close scores the
 * contest if this job has not run yet.
 */
@Component
public class ContestScheduler {

    private static final Logger log = LoggerFactory.getLogger(ContestScheduler.class);

    private final QuizPlanner planner;
    private final ContestSessionService contestSessionService;
    private final EnrollmentService enrollmentService;

    public ContestScheduler(QuizPlanner planner,
                            ContestSessionService contestSessionService,
                            EnrollmentService enrollmentService) {
        this.planner = planner;
        this.contestSessionService = contestSessionService;
        this.enrollmentService = enrollmentService;
    }

    @Scheduled(cron = "${app.contest.open-cron}", zone = "${app.schedule.zone}")
    public void open() {
        LocalDate today = enrollmentService.today();
        QuizPlanner.PlanResult result = planner.plan(today);
        log.info("Opened {} contests for {}. Quizzes not ready yet: {}",
                result.contests(), today, result.queued() + result.requeued());
    }

    @Scheduled(cron = "${app.contest.close-cron}", zone = "${app.schedule.zone}")
    public void close() {
        int scored = contestSessionService.scoreClosed();
        if (scored > 0) {
            log.info("Scored {} closed contests", scored);
        }
    }
}
