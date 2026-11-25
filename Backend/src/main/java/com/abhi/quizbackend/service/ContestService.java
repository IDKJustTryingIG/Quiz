package com.abhi.quizbackend.service;

import com.abhi.quizbackend.entity.AttemptStatus;
import com.abhi.quizbackend.entity.Enrollment;
import com.abhi.quizbackend.entity.EnrollmentStatus;
import com.abhi.quizbackend.entity.Quiz;
import com.abhi.quizbackend.entity.QuizAnswer;
import com.abhi.quizbackend.entity.QuizAttempt;
import com.abhi.quizbackend.entity.QuizQuestion;
import com.abhi.quizbackend.entity.QuizSession;
import com.abhi.quizbackend.entity.SessionDay;
import com.abhi.quizbackend.exception.ContestConflictException;
import com.abhi.quizbackend.exception.ResourceNotFoundException;
import com.abhi.quizbackend.models.AnswerRequest;
import com.abhi.quizbackend.models.AttemptResponse;
import com.abhi.quizbackend.models.AttemptResultResponse;
import com.abhi.quizbackend.models.ContestTodayResponse;
import com.abhi.quizbackend.models.ContestTodayResponse.State;
import com.abhi.quizbackend.models.LeaderboardResponse;
import com.abhi.quizbackend.models.QuizQuestionView;
import com.abhi.quizbackend.models.QuizResultResponse.QuestionResult;
import com.abhi.quizbackend.properties.ContestProperties;
import com.abhi.quizbackend.repository.EnrollmentRepository;
import com.abhi.quizbackend.repository.QuizAttemptRepository;
import com.abhi.quizbackend.repository.QuizRepository;
import com.abhi.quizbackend.repository.QuizSessionRepository;
import com.abhi.quizbackend.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The contest from the user's side: see today's contest, start, answer,
 * submit, and see the ranking.
 *
 * The server keeps the time. An answer after the deadline is refused, and
 * the correct answers stay hidden until the contest closes.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ContestService {

    private final EnrollmentRepository enrollmentRepository;
    private final QuizSessionRepository sessionRepository;
    private final QuizAttemptRepository attemptRepository;
    private final QuizRepository quizRepository;
    private final UserRepository userRepository;
    private final EnrollmentService enrollmentService;
    private final ContestSessionService contestSessionService;
    private final TrackService trackService;
    private final AttemptScorer scorer;
    private final ContestProperties contestProperties;
    private final Clock clock;

    /** Where the user is: their enrollment, today's contest, and their day group in it. */
    private record Place(Enrollment enrollment, int dayNumber, int totalDays,
                         QuizSession session, SessionDay day) {
    }

    public ContestTodayResponse today(Long userId) {
        Instant now = clock.instant();
        Optional<Enrollment> enrollment = enrollmentRepository.findByUserUserIdAndStatus(userId, EnrollmentStatus.ACTIVE);
        if (enrollment.isEmpty()) {
            return ContestTodayResponse.none(now);
        }

        Enrollment e = enrollment.get();
        LocalDate date = enrollmentService.today();
        int dayNumber = EnrollmentService.dayNumber(e.getStartDate(), date);
        int totalDays = e.getTrack().getTrackTopics().size();
        if (dayNumber < 1 || dayNumber > totalDays) {
            return ContestTodayResponse.none(now);
        }

        String topicName = e.getTrack().getTrackTopics().get(dayNumber - 1).getTopic().getName();
        Optional<QuizSession> session = sessionRepository.findByTrackTrackIdAndSessionDate(e.getTrack().getTrackId(), date);
        Optional<SessionDay> day = session.flatMap(s -> dayOf(s, dayNumber));
        if (day.isEmpty()) {
            return new ContestTodayResponse(State.NOT_PLANNED, now, null, e.getTrack().getTrackId(),
                    e.getTrack().getName(), e.getTrack().getSlug(), date, dayNumber, totalDays, topicName,
                    null, null, null, false, false, null);
        }

        QuizSession s = session.get();
        contestSessionService.scoreIfDue(s);
        QuizAttempt attempt = attemptRepository.findBySessionSessionIdAndUserUserId(s.getSessionId(), userId)
                .orElse(null);
        State state = now.isBefore(s.getOpensAt()) ? State.UPCOMING
                : s.isClosedAt(now) ? State.CLOSED
                : State.OPEN;
        boolean quizReady = day.get().getQuizId() != null;
        boolean canStart = state == State.OPEN && attempt == null && quizReady && now.isBefore(s.getStartDeadline());

        return new ContestTodayResponse(state, now, s.getSessionId(), e.getTrack().getTrackId(),
                e.getTrack().getName(), e.getTrack().getSlug(), date, dayNumber, totalDays, topicName,
                s.getOpensAt(), s.getStartDeadline(), s.getClosesAt(), quizReady, canStart,
                attempt == null ? null : summary(attempt));
    }

    /**
     * Starts the user's attempt, or returns the one they already started.
     * The time limit starts now.
     */
    public AttemptResponse start(Long sessionId, Long userId) {
        Place place = placeIn(sessionId, userId);
        QuizSession session = place.session();
        Instant now = clock.instant();

        Optional<QuizAttempt> existing = attemptRepository.findBySessionSessionIdAndUserUserId(sessionId, userId);
        if (existing.isPresent()) {
            return toResponse(existing.get(), now);
        }
        if (now.isBefore(session.getOpensAt())) {
            throw new ContestConflictException("The contest has not opened yet.");
        }
        if (!now.isBefore(session.getStartDeadline())) {
            throw new ContestConflictException("The time to start is over. You can see the ranking.");
        }
        Long quizId = place.day().getQuizId();
        if (quizId == null) {
            throw new ContestConflictException("The quiz for your day is not ready yet. Try again in a minute.");
        }

        Quiz quiz = loadQuiz(quizId);
        Instant limit = now.plus(contestProperties.getAttemptTime());
        QuizAttempt attempt = QuizAttempt.builder()
                .session(session)
                .user(userRepository.getReferenceById(userId))
                .dayNumber(place.dayNumber())
                .quizId(quizId)
                .status(AttemptStatus.IN_PROGRESS)
                .startedAt(now)
                .deadlineAt(limit.isBefore(session.getClosesAt()) ? limit : session.getClosesAt())
                .total(quiz.getQuestions().size())
                .build();
        try {
            attemptRepository.saveAndFlush(attempt);
        } catch (DataIntegrityViolationException e) {
            // A second tab started at the same moment. The transaction must
            // roll back, so the user opens the attempt again.
            throw new ContestConflictException("You already started this contest. Reload the page.");
        }
        return toResponse(attempt, quiz, now);
    }

    /** The user's attempt, to resume it after a reload. */
    @Transactional(readOnly = true)
    public AttemptResponse attempt(Long attemptId, Long userId) {
        return toResponse(ownAttempt(attemptId, userId), clock.instant());
    }

    /** Saves one answer. The first answer to a question is final. */
    public void answer(Long attemptId, AnswerRequest request, Long userId) {
        QuizAttempt attempt = ownAttempt(attemptId, userId);
        Instant now = clock.instant();
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new ContestConflictException("This attempt is already finished.");
        }
        if (!now.isBefore(attempt.getDeadlineAt())) {
            throw new ContestConflictException("The time is up. Your saved answers count.");
        }

        Quiz quiz = loadQuiz(attempt.getQuizId());
        boolean inQuiz = quiz.getQuestions().stream()
                .anyMatch(q -> q.getQuizQuestionId().equals(request.questionId()));
        if (!inQuiz) {
            throw new ResourceNotFoundException("This question is not part of your quiz.");
        }

        Optional<QuizAnswer> given = attempt.getAnswers().stream()
                .filter(a -> a.getQuestionId().equals(request.questionId()))
                .findFirst();
        if (given.isPresent()) {
            if (given.get().getChosenIndex() != request.chosenIndex().shortValue()) {
                throw new ContestConflictException("You already answered this question.");
            }
            return;
        }

        attempt.getAnswers().add(QuizAnswer.builder()
                .attempt(attempt)
                .questionId(request.questionId())
                .chosenIndex(request.chosenIndex().shortValue())
                .answeredAt(now)
                .build());
    }

    /** Ends the attempt and scores it. After the deadline, it counts as EXPIRED. */
    public AttemptResultResponse submit(Long attemptId, Long userId) {
        QuizAttempt attempt = ownAttempt(attemptId, userId);
        if (attempt.getStatus() == AttemptStatus.IN_PROGRESS) {
            Instant now = clock.instant();
            if (now.isBefore(attempt.getDeadlineAt())) {
                scorer.score(attempt, now, AttemptStatus.SUBMITTED);
            } else {
                scorer.score(attempt, attempt.getDeadlineAt(), AttemptStatus.EXPIRED);
            }
        }
        return toResult(attempt);
    }

    /** The score, and after the close, every correct answer. */
    public AttemptResultResponse result(Long attemptId, Long userId) {
        QuizAttempt attempt = ownAttempt(attemptId, userId);
        contestSessionService.scoreIfDue(attempt.getSession());
        return toResult(attempt);
    }

    /** The ranking of a contest, for anyone who can see the track. */
    public LeaderboardResponse leaderboard(Long sessionId, Long userId) {
        QuizSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Contest not found"));
        trackService.findReadableTrack(session.getTrack().getTrackId(), userId);
        contestSessionService.scoreIfDue(session);

        List<QuizAttempt> attempts = attemptRepository.findBySessionSessionId(sessionId);
        List<QuizAttempt> scored = attempts.stream()
                .filter(QuizAttempt::isScored)
                .sorted(Comparator.comparing(QuizAttempt::getScore, Comparator.reverseOrder())
                        .thenComparing(QuizAttempt::getDurationMs))
                .toList();

        List<LeaderboardResponse.Entry> entries = new ArrayList<>();
        // Equal score and equal time share a rank: 1, 2, 2, 4.
        for (int i = 0; i < scored.size(); i++) {
            QuizAttempt a = scored.get(i);
            int rank = i + 1;
            if (i > 0) {
                QuizAttempt previous = scored.get(i - 1);
                if (previous.getScore().equals(a.getScore()) && previous.getDurationMs().equals(a.getDurationMs())) {
                    rank = entries.get(i - 1).rank();
                }
            }
            entries.add(entry(rank, a, userId));
        }
        attempts.stream()
                .filter(a -> !a.isScored())
                .forEach(a -> entries.add(entry(null, a, userId)));

        Instant now = clock.instant();
        return new LeaderboardResponse(sessionId, session.getTrack().getName(), session.getSessionDate(),
                session.isClosedAt(now), session.getClosesAt(), now, entries);
    }

    // ---------- helpers ----------

    /** The user's day group in this contest. The user must follow the contest's track today. */
    private Place placeIn(Long sessionId, Long userId) {
        QuizSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Contest not found"));
        Enrollment enrollment = enrollmentRepository.findByUserUserIdAndStatus(userId, EnrollmentStatus.ACTIVE)
                .filter(e -> e.getTrack().getTrackId().equals(session.getTrack().getTrackId()))
                .orElseThrow(() -> new ContestConflictException("You are not enrolled in this track."));

        int dayNumber = EnrollmentService.dayNumber(enrollment.getStartDate(), session.getSessionDate());
        SessionDay day = dayOf(session, dayNumber)
                .orElseThrow(() -> new ContestConflictException("You have no quiz in this contest."));
        return new Place(enrollment, dayNumber, enrollment.getTrack().getTrackTopics().size(), session, day);
    }

    private static Optional<SessionDay> dayOf(QuizSession session, int dayNumber) {
        return session.getDays().stream().filter(d -> d.getDayNumber() == dayNumber).findFirst();
    }

    private QuizAttempt ownAttempt(Long attemptId, Long userId) {
        // 404 rather than 403, so attempt ids of other users are not discoverable.
        return attemptRepository.findById(attemptId)
                .filter(a -> a.getUser().getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Attempt not found"));
    }

    private Quiz loadQuiz(Long quizId) {
        return Optional.ofNullable(quizId)
                .flatMap(quizRepository::findWithQuestionsByQuizId)
                .orElseThrow(() -> new ResourceNotFoundException("The quiz of this contest no longer exists."));
    }

    private AttemptResponse toResponse(QuizAttempt attempt, Instant now) {
        return toResponse(attempt, loadQuiz(attempt.getQuizId()), now);
    }

    private static AttemptResponse toResponse(QuizAttempt attempt, Quiz quiz, Instant now) {
        List<QuizQuestionView> questions = quiz.getQuestions().stream()
                .map(q -> new QuizQuestionView(q.getQuizQuestionId(), q.getQuestion(),
                        List.of(q.getOptionA(), q.getOptionB(), q.getOptionC(), q.getOptionD()), q.getSourceFile()))
                .toList();
        List<AttemptResponse.SavedAnswer> answers = attempt.getAnswers().stream()
                .map(a -> new AttemptResponse.SavedAnswer(a.getQuestionId(), a.getChosenIndex()))
                .toList();
        return new AttemptResponse(attempt.getAttemptId(), attempt.getSession().getSessionId(),
                attempt.getStatus().name(), attempt.getStartedAt(), attempt.getDeadlineAt(), now, questions, answers);
    }

    private AttemptResultResponse toResult(QuizAttempt attempt) {
        QuizSession session = attempt.getSession();
        boolean revealed = session.isClosedAt(clock.instant()) && attempt.isScored();

        List<QuestionResult> results = List.of();
        if (revealed && attempt.getQuizId() != null) {
            Map<Long, QuizAnswer> answers = attempt.getAnswers().stream()
                    .collect(Collectors.toMap(QuizAnswer::getQuestionId, Function.identity()));
            results = quizRepository.findWithQuestionsByQuizId(attempt.getQuizId())
                    .map(quiz -> quiz.getQuestions().stream()
                            .map(q -> result(q, answers.get(q.getQuizQuestionId())))
                            .toList())
                    .orElse(List.of());
        }

        return new AttemptResultResponse(attempt.getAttemptId(), attempt.getStatus().name(), attempt.getScore(),
                attempt.getTotal(), attempt.getDurationMs(), revealed, session.getClosesAt(), results);
    }

    private static QuestionResult result(QuizQuestion q, QuizAnswer answer) {
        Integer chosen = answer == null ? null : (int) answer.getChosenIndex();
        return new QuestionResult(q.getQuizQuestionId(), chosen, q.getCorrectIndex(),
                answer != null && Boolean.TRUE.equals(answer.getCorrect()), q.getExplanation());
    }

    private static ContestTodayResponse.AttemptSummary summary(QuizAttempt a) {
        return new ContestTodayResponse.AttemptSummary(a.getAttemptId(), a.getStatus().name(), a.getDeadlineAt(),
                a.getScore(), a.getTotal(), a.getDurationMs());
    }

    private static LeaderboardResponse.Entry entry(Integer rank, QuizAttempt a, Long userId) {
        return new LeaderboardResponse.Entry(rank, a.getUser().getUsername(), a.getDayNumber(), a.getStatus().name(),
                a.getScore(), a.getTotal(), a.getDurationMs(), a.getUser().getUserId().equals(userId));
    }
}
