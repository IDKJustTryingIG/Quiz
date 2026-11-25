package com.abhi.quizbackend.service;

import com.abhi.quizbackend.properties.QuizJobProperties;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;

/**
 * The clock of the quiz queue.
 *
 * - At 06:00 and 17:00 it plans the day's quizzes. 06:00 leaves the whole day
 *   for retries. 17:00 picks up file changes made during the day.
 * - Every few seconds it starts workers, up to app.quiz.jobs.workers at once.
 *
 * The workers run on their own threads. Spring runs every @Scheduled method
 * on one shared thread, and a model call must not block the other jobs.
 */
@Component
public class QuizJobScheduler {

    private static final Logger log = LoggerFactory.getLogger(QuizJobScheduler.class);

    private final QuizPlanner planner;
    private final QuizJobWorker worker;
    private final EnrollmentService enrollmentService;
    private final Semaphore freeWorkers;
    private final ThreadPoolTaskExecutor executor;

    public QuizJobScheduler(
            QuizPlanner planner,
            QuizJobWorker worker,
            EnrollmentService enrollmentService,
            QuizJobProperties properties) {
        this.planner = planner;
        this.worker = worker;
        this.enrollmentService = enrollmentService;
        this.freeWorkers = new Semaphore(properties.getWorkers());

        // Not a bean: a bean of this type would replace Spring Boot's own
        // application task executor.
        this.executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getWorkers());
        executor.setMaxPoolSize(properties.getWorkers());
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("quiz-job-");
        executor.initialize();
    }

    @Scheduled(cron = "${app.quiz.jobs.plan-morning-cron}", zone = "${app.schedule.zone}")
    public void planMorning() {
        plan();
    }

    @Scheduled(cron = "${app.quiz.jobs.plan-evening-cron}", zone = "${app.schedule.zone}")
    public void planEvening() {
        plan();
    }

    private void plan() {
        QuizPlanner.PlanResult result = planner.plan(enrollmentService.today());
        log.info("Planned quizzes for {}: {} needed, {} queued, {} already ready, {} queued again",
                result.date(), result.needed(), result.queued(), result.alreadyReady(), result.requeued());
    }

    @Scheduled(fixedDelayString = "${app.quiz.jobs.poll-delay}", initialDelayString = "${app.quiz.jobs.poll-delay}")
    public void startWorkers() {
        worker.releaseStale();
        if (!worker.hasWork()) {
            return;
        }
        while (freeWorkers.tryAcquire()) {
            try {
                executor.execute(this::drainQueue);
            } catch (TaskRejectedException e) {
                freeWorkers.release();
                return;
            }
        }
    }

    /** One worker: takes jobs until none is waiting. */
    private void drainQueue() {
        try {
            while (worker.processNext()) {
                // The next job.
            }
        } catch (RuntimeException e) {
            // A database error. The job stays RUNNING, and releaseStale() frees it later.
            log.error("A quiz worker stopped", e);
        } finally {
            freeWorkers.release();
        }
    }

    @PreDestroy
    void stop() {
        executor.shutdown();
    }
}
