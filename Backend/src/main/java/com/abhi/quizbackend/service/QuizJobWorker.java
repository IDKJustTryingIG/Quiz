package com.abhi.quizbackend.service;

import com.abhi.quizbackend.entity.QuizGenerationJob;
import com.abhi.quizbackend.entity.QuizJobStatus;
import com.abhi.quizbackend.exception.QuizSourceException;
import com.abhi.quizbackend.exception.ResourceNotFoundException;
import com.abhi.quizbackend.properties.QuizJobProperties;
import com.abhi.quizbackend.repository.QuizGenerationJobRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Takes one job from the queue and makes its quiz.
 *
 * Three short transactions per job: one to take it, none during the model
 * call (up to a minute), and one to record the result. A database connection
 * is never held while the model writes.
 */
@Service
public class QuizJobWorker {

    private static final Logger log = LoggerFactory.getLogger(QuizJobWorker.class);

    private final QuizGenerationJobRepository jobRepository;
    private final QuizCacheService quizCacheService;
    private final ContestSessionService contestSessionService;
    private final QuizJobProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public QuizJobWorker(
            QuizGenerationJobRepository jobRepository,
            QuizCacheService quizCacheService,
            ContestSessionService contestSessionService,
            QuizJobProperties properties,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.jobRepository = jobRepository;
        this.quizCacheService = quizCacheService;
        this.contestSessionService = contestSessionService;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** The ids a worker needs after the claim transaction ends. */
    private record Claimed(Long jobId, Long trackId, Long topicId, LocalDate runDate, int attempts) {
    }

    /**
     * Takes the next job whose delay is over, and makes its quiz.
     *
     * @return false if no job was waiting
     */
    public boolean processNext() {
        Optional<Claimed> claimed = transactionTemplate.execute(status -> claim());
        if (claimed == null || claimed.isEmpty()) {
            return false;
        }
        run(claimed.get());
        return true;
    }

    /** Whether a job is waiting now. Lets the runner skip starting idle workers. */
    public boolean hasWork() {
        return jobRepository.existsClaimable(clock.instant());
    }

    /** Puts jobs whose worker stopped back in the queue. */
    public void releaseStale() {
        Integer released = transactionTemplate.execute(status ->
                jobRepository.releaseStale(clock.instant().minus(properties.getStaleAfter())));
        if (released != null && released > 0) {
            log.warn("Put {} quiz jobs back in the queue: their worker stopped", released);
        }
    }

    private Optional<Claimed> claim() {
        Instant now = clock.instant();
        return jobRepository.findClaimable(now, PageRequest.of(0, 1)).stream()
                .findFirst()
                .map(job -> {
                    job.setStatus(QuizJobStatus.RUNNING);
                    job.setAttempts(job.getAttempts() + 1);
                    job.setLockedAt(now);
                    return new Claimed(job.getJobId(), job.getTrack().getTrackId(),
                            job.getTopic().getTopicId(), job.getRunDate(), job.getAttempts());
                });
    }

    private void run(Claimed job) {
        try {
            Long quizId = quizCacheService.prepare(job.trackId(), job.topicId());
            update(job, j -> {
                j.setStatus(QuizJobStatus.DONE);
                j.setQuizId(quizId);
                j.setLastError(null);
                j.setFinishedAt(clock.instant());
            });
            // The day's contests use the new quiz, unless they already opened with another.
            contestSessionService.pin(job.topicId(), job.runDate(), quizId);
        } catch (QuizSourceException | ResourceNotFoundException e) {
            // Trying again cannot help until someone changes the track.
            log.warn("Quiz job {} (topic {}) failed: {}", job.jobId(), job.topicId(), e.getMessage());
            update(job, j -> fail(j, e.getMessage()));
        } catch (RuntimeException e) {
            boolean last = job.attempts() >= properties.getMaxAttempts();
            log.warn("Quiz job {} (topic {}) failed, attempt {} of {}",
                    job.jobId(), job.topicId(), job.attempts(), properties.getMaxAttempts(), e);
            update(job, j -> {
                if (last) {
                    fail(j, describe(e));
                } else {
                    j.setStatus(QuizJobStatus.PENDING);
                    j.setLockedAt(null);
                    j.setLastError(describe(e));
                    j.setNextAttemptAt(clock.instant().plus(delayAfter(job.attempts())));
                }
            });
        }
    }

    /** 1, 2, 4 ... times the retry delay. */
    private Duration delayAfter(int attempts) {
        return properties.getRetryDelay().multipliedBy(1L << Math.min(attempts - 1, 10));
    }

    private void fail(QuizGenerationJob job, String reason) {
        job.setStatus(QuizJobStatus.FAILED);
        job.setLockedAt(null);
        job.setLastError(reason);
        job.setFinishedAt(clock.instant());
    }

    /** Shown on the admin page, so the class name says what kind of failure it was. */
    private static String describe(RuntimeException e) {
        return e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
    }

    private void update(Claimed claimed, Consumer<QuizGenerationJob> change) {
        transactionTemplate.executeWithoutResult(status ->
                // Gone if the track was deleted while the model wrote.
                jobRepository.findById(claimed.jobId()).ifPresent(change));
    }
}
