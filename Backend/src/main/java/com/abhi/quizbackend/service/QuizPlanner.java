package com.abhi.quizbackend.service;

import com.abhi.quizbackend.entity.QuizGenerationJob;
import com.abhi.quizbackend.entity.QuizJobStatus;
import com.abhi.quizbackend.entity.TrackTopic;
import com.abhi.quizbackend.repository.EnrollmentRepository;
import com.abhi.quizbackend.repository.QuizGenerationJobRepository;
import com.abhi.quizbackend.repository.TopicRepository;
import com.abhi.quizbackend.repository.TrackRepository;
import com.abhi.quizbackend.repository.TrackStart;
import com.abhi.quizbackend.repository.TrackTopicRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Decides which quizzes a day needs, and puts them in the job queue. It also
 * creates the day's contests. It never calls the model: the workers do that.
 *
 * Only topics that an enrolled user needs on the date are planned, and each
 * topic once, however many users need it. A topic that already has a current
 * quiz gets a DONE job at once, at no cost.
 *
 * Planning the same date again is safe. It adds jobs for new enrollments,
 * and queues again a DONE job whose quiz is no longer current (the files
 * changed) and a FAILED job (the owner may have fixed the files).
 */
@Service
@RequiredArgsConstructor
public class QuizPlanner {

    /** What one planning run did, for the log and the admin page. */
    public record PlanResult(LocalDate date, int needed, int queued, int alreadyReady, int requeued, int contests) {
    }

    private final EnrollmentRepository enrollmentRepository;
    private final TrackTopicRepository trackTopicRepository;
    private final QuizGenerationJobRepository jobRepository;
    private final TrackRepository trackRepository;
    private final TopicRepository topicRepository;
    private final QuizCacheService quizCacheService;
    private final ContestSessionService contestSessionService;
    private final Clock clock;

    /**
     * Plans the date: queues the quizzes it needs, creates its contests, and
     * pins the quizzes that are ready.
     */
    @Transactional
    public PlanResult plan(LocalDate date) {
        List<ContestSessionService.DayGroup> groups = dayGroups(date);
        Map<Long, Long> trackByTopic = new LinkedHashMap<>();
        groups.forEach(group -> trackByTopic.putIfAbsent(group.topicId(), group.trackId()));
        Map<Long, QuizGenerationJob> existing = jobRepository.findByRunDate(date).stream()
                .collect(Collectors.toMap(job -> job.getTopic().getTopicId(), Function.identity()));
        Instant now = clock.instant();

        int queued = 0;
        int alreadyReady = 0;
        int requeued = 0;
        for (Map.Entry<Long, Long> entry : trackByTopic.entrySet()) {
            Long topicId = entry.getKey();
            Long trackId = entry.getValue();
            Optional<Long> current = quizCacheService.findCurrent(trackId, topicId);
            QuizGenerationJob job = existing.get(topicId);

            if (job == null) {
                job = QuizGenerationJob.builder()
                        .track(trackRepository.getReferenceById(trackId))
                        .topic(topicRepository.getReferenceById(topicId))
                        .runDate(date)
                        .nextAttemptAt(now)
                        .build();
                if (current.isPresent()) {
                    markDone(job, current.get(), now);
                    alreadyReady++;
                } else {
                    job.setStatus(QuizJobStatus.PENDING);
                    queued++;
                }
                jobRepository.save(job);
            } else if (job.getStatus() == QuizJobStatus.DONE) {
                if (current.isEmpty()) {
                    requeue(job, now);
                    requeued++;
                } else {
                    // The owner may have made the quiz by hand since.
                    job.setQuizId(current.get());
                }
            } else if (job.getStatus() == QuizJobStatus.FAILED) {
                requeue(job, now);
                requeued++;
            }
            // PENDING and RUNNING jobs are already in the queue.
        }

        int contests = contestSessionService.ensureSessions(date, groups);
        contestSessionService.pinReady(date);

        return new PlanResult(date, trackByTopic.size(), queued, alreadyReady, requeued, contests);
    }

    /**
     * Every (track, day) that some active enrollment is on at the date, with
     * the day's topic. Users who have not started, or who went past the last
     * day, need nothing.
     */
    private List<ContestSessionService.DayGroup> dayGroups(LocalDate date) {
        List<TrackStart> starts = enrollmentRepository.findActiveStarts(date);
        Map<Long, List<TrackTopic>> daysByTrack = new LinkedHashMap<>();
        Set<ContestSessionService.DayGroup> groups = new LinkedHashSet<>();

        for (TrackStart start : starts) {
            List<TrackTopic> days = daysByTrack.computeIfAbsent(
                    start.trackId(), trackTopicRepository::findByTrackTrackIdOrderByPositionAsc);
            int dayNumber = EnrollmentService.dayNumber(start.startDate(), date);
            if (dayNumber >= 1 && dayNumber <= days.size()) {
                groups.add(new ContestSessionService.DayGroup(
                        start.trackId(), dayNumber, days.get(dayNumber - 1).getTopic().getTopicId()));
            }
        }
        return List.copyOf(groups);
    }

    private static void markDone(QuizGenerationJob job, Long quizId, Instant now) {
        job.setStatus(QuizJobStatus.DONE);
        job.setQuizId(quizId);
        job.setLastError(null);
        job.setFinishedAt(now);
    }

    private static void requeue(QuizGenerationJob job, Instant now) {
        job.setStatus(QuizJobStatus.PENDING);
        job.setAttempts(0);
        job.setNextAttemptAt(now);
        job.setLockedAt(null);
        job.setLastError(null);
        job.setQuizId(null);
        job.setFinishedAt(null);
    }
}
