package com.abhi.quizbackend.service;

import com.abhi.quizbackend.entity.AttemptStatus;
import com.abhi.quizbackend.entity.QuizAttempt;
import com.abhi.quizbackend.entity.QuizSession;
import com.abhi.quizbackend.entity.SessionDay;
import com.abhi.quizbackend.exception.ResourceNotFoundException;
import com.abhi.quizbackend.properties.ContestProperties;
import com.abhi.quizbackend.properties.ScheduleProperties;
import com.abhi.quizbackend.repository.QuizAttemptRepository;
import com.abhi.quizbackend.repository.QuizSessionRepository;
import com.abhi.quizbackend.repository.SessionDayRepository;
import com.abhi.quizbackend.repository.TopicRepository;
import com.abhi.quizbackend.repository.TrackRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Creates the day's contests, pins their quizzes, and scores the attempts
 * that nobody submitted. The users' side is in {@link ContestService}.
 */
@Service
@RequiredArgsConstructor
public class ContestSessionService {

    /** The users on dayNumber of a track need this topic's quiz. */
    public record DayGroup(Long trackId, int dayNumber, Long topicId) {
    }

    private final QuizSessionRepository sessionRepository;
    private final SessionDayRepository sessionDayRepository;
    private final QuizAttemptRepository attemptRepository;
    private final TrackRepository trackRepository;
    private final TopicRepository topicRepository;
    private final QuizCacheService quizCacheService;
    private final AttemptScorer scorer;
    private final ContestProperties contestProperties;
    private final ScheduleProperties scheduleProperties;
    private final Clock clock;

    /**
     * Makes sure each track has a contest on the date, with one day group per
     * day its users are on. Safe to repeat.
     *
     * Before the contest opens, a day group follows the track: if the owner
     * moved the days, the group gets the new topic and loses its quiz. After
     * it opens, existing groups never change.
     *
     * @return the number of contests on the date
     */
    @Transactional
    public int ensureSessions(LocalDate date, List<DayGroup> groups) {
        Instant now = clock.instant();
        Map<Long, List<DayGroup>> byTrack = groups.stream()
                .collect(Collectors.groupingBy(DayGroup::trackId, LinkedHashMap::new, Collectors.toList()));

        byTrack.forEach((trackId, trackGroups) -> {
            QuizSession session = sessionRepository.findByTrackTrackIdAndSessionDate(trackId, date)
                    .orElseGet(() -> sessionRepository.save(newSession(trackId, date)));
            boolean beforeOpen = now.isBefore(session.getOpensAt());

            for (DayGroup group : trackGroups) {
                Optional<SessionDay> existing = session.getDays().stream()
                        .filter(day -> day.getDayNumber() == group.dayNumber())
                        .findFirst();
                if (existing.isEmpty()) {
                    session.getDays().add(SessionDay.builder()
                            .session(session)
                            .dayNumber(group.dayNumber())
                            .topic(topicRepository.getReferenceById(group.topicId()))
                            .build());
                } else if (beforeOpen && !group.topicId().equals(topicIdOf(existing.get()))) {
                    existing.get().setTopic(topicRepository.getReferenceById(group.topicId()));
                    existing.get().setQuizId(null);
                }
            }
        });
        return byTrack.size();
    }

    /**
     * Pins each day group of the date to its topic's current quiz. Before the
     * contest opens, a newer quiz replaces the pinned one. After it opens,
     * only a group with no quiz gets one.
     *
     * A group whose topic has no current quiz keeps the quiz it has: an old
     * quiz is better than none.
     */
    @Transactional
    public void pinReady(LocalDate date) {
        Instant now = clock.instant();
        for (QuizSession session : sessionRepository.findBySessionDate(date)) {
            boolean beforeOpen = now.isBefore(session.getOpensAt());
            for (SessionDay day : session.getDays()) {
                if (day.getTopic() == null || (!beforeOpen && day.getQuizId() != null)) {
                    continue;
                }
                try {
                    quizCacheService.findCurrent(session.getTrack().getTrackId(), day.getTopic().getTopicId())
                            .ifPresent(day::setQuizId);
                } catch (ResourceNotFoundException e) {
                    // The owner removed the day from the track. The group keeps what it has.
                }
            }
        }
    }

    /** Pins a quiz a worker just made, by the same rules as {@link #pinReady}. */
    @Transactional
    public void pin(Long topicId, LocalDate date, Long quizId) {
        sessionDayRepository.pin(topicId, date, quizId, clock.instant());
    }

    /** Scores every closed contest that is not scored yet. */
    @Transactional
    public int scoreClosed() {
        Instant now = clock.instant();
        List<QuizSession> due = sessionRepository.findByClosesAtLessThanEqualAndScoredAtIsNull(now);
        due.forEach(session -> scoreSession(session, now));
        return due.size();
    }

    /**
     * Scores the attempts of a closed contest that nobody submitted, from the
     * answers they saved. Runs once per contest. Call it inside a transaction.
     */
    public void scoreIfDue(QuizSession session) {
        Instant now = clock.instant();
        if (session.isClosedAt(now) && session.getScoredAt() == null) {
            scoreSession(session, now);
        }
    }

    private void scoreSession(QuizSession session, Instant now) {
        for (QuizAttempt attempt : attemptRepository.findBySessionSessionIdAndStatus(
                session.getSessionId(), AttemptStatus.IN_PROGRESS)) {
            scorer.score(attempt, attempt.getDeadlineAt(), AttemptStatus.EXPIRED);
        }
        session.setScoredAt(now);
    }

    private QuizSession newSession(Long trackId, LocalDate date) {
        Instant opensAt = date.atTime(contestProperties.getOpensAt())
                .atZone(scheduleProperties.getZone())
                .toInstant();
        return QuizSession.builder()
                .track(trackRepository.getReferenceById(trackId))
                .sessionDate(date)
                .opensAt(opensAt)
                .startDeadline(opensAt.plus(contestProperties.getStartWindow()))
                .closesAt(opensAt.plus(contestProperties.getLength()))
                .build();
    }

    private static Long topicIdOf(SessionDay day) {
        return day.getTopic() == null ? null : day.getTopic().getTopicId();
    }
}
