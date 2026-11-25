package com.abhi.quizbackend.service;

import com.abhi.quizbackend.entity.Quiz;
import com.abhi.quizbackend.entity.QuizQuestion;
import com.abhi.quizbackend.exception.QuizLockedException;
import com.abhi.quizbackend.exception.QuizNotReadyException;
import com.abhi.quizbackend.exception.ResourceNotFoundException;
import com.abhi.quizbackend.models.CheckAnswersRequest;
import com.abhi.quizbackend.models.QuizQuestionView;
import com.abhi.quizbackend.models.QuizResponse;
import com.abhi.quizbackend.models.QuizResultResponse;
import com.abhi.quizbackend.models.QuizResultResponse.QuestionResult;
import com.abhi.quizbackend.repository.QuizRepository;
import com.abhi.quizbackend.repository.SessionDayRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Serves practice quizzes to users and scores their answers. The correct
 * answers stay on the server until the user submits.
 *
 * A quiz that a contest still uses is locked: practice would show its
 * questions before the contest.
 *
 * No method here holds a transaction around {@link QuizCacheService#prepare},
 * because the model call takes up to a minute.
 */
@Service
@RequiredArgsConstructor
public class QuizService {

    private final TrackService trackService;
    private final QuizCacheService quizCacheService;
    private final QuizRepository quizRepository;
    private final SessionDayRepository sessionDayRepository;
    private final Clock clock;

    /**
     * The day's current quiz, without answers. Never calls the model.
     *
     * @throws QuizNotReadyException if the day has no current quiz
     */
    public QuizResponse getQuiz(Long trackId, Long topicId, Long userId) {
        trackService.findReadableTrack(trackId, userId);
        Long quizId = quizCacheService.findCurrent(trackId, topicId)
                .orElseThrow(QuizNotReadyException::new);
        requireUnlocked(quizId);
        return toResponse(trackId, topicId, load(quizId));
    }

    /**
     * Makes the day's quiz now, if it has none. Only the track owner may do
     * this, because it calls the model.
     *
     * Returns the quiz's id and size only. The owner may also compete, so
     * the questions come through {@link #getQuiz}, which respects the lock.
     */
    public PreparedQuiz prepare(Long trackId, Long topicId, Long userId) {
        trackService.findOwnedTrack(trackId, userId);
        Long quizId = quizCacheService.prepare(trackId, topicId);
        return new PreparedQuiz(quizId, load(quizId).getQuestions().size());
    }

    public record PreparedQuiz(Long quizId, int questionCount) {
    }

    /**
     * Scores the answers to one quiz of the day. The quiz may be an older
     * version than the current one, if the files changed while the user answered.
     */
    public QuizResultResponse check(Long trackId, Long topicId, CheckAnswersRequest request, Long userId) {
        trackService.findReadableTrack(trackId, userId);
        // Confirms that the topic belongs to the track.
        quizCacheService.findCurrent(trackId, topicId);

        Quiz quiz = quizRepository.findWithQuestionsByQuizId(request.quizId())
                .filter(q -> q.getTopic().getTopicId().equals(topicId))
                .orElseThrow(() -> new ResourceNotFoundException("This quiz no longer exists. Open the quiz again."));
        requireUnlocked(quiz.getQuizId());

        // The first answer to a question counts. Unknown questions are ignored.
        Map<Long, Integer> chosen = new HashMap<>();
        request.answers().forEach(a -> chosen.putIfAbsent(a.questionId(), a.chosenIndex()));

        List<QuestionResult> results = quiz.getQuestions().stream()
                .map(q -> {
                    Integer choice = chosen.get(q.getQuizQuestionId());
                    int correctIndex = q.getCorrectIndex();
                    return new QuestionResult(
                            q.getQuizQuestionId(),
                            choice,
                            correctIndex,
                            choice != null && choice == correctIndex,
                            q.getExplanation());
                })
                .toList();

        int score = (int) results.stream().filter(QuestionResult::correct).count();
        return new QuizResultResponse(quiz.getQuizId(), score, results.size(), results);
    }

    private void requireUnlocked(Long quizId) {
        if (sessionDayRepository.isInUnclosedContest(quizId, clock.instant())) {
            throw new QuizLockedException();
        }
    }

    private Quiz load(Long quizId) {
        // Gone if the files changed between finding the quiz and reading it.
        return quizRepository.findWithQuestionsByQuizId(quizId)
                .orElseThrow(QuizNotReadyException::new);
    }

    private static QuizResponse toResponse(Long trackId, Long topicId, Quiz quiz) {
        List<QuizQuestionView> questions = quiz.getQuestions().stream()
                .map(QuizService::toView)
                .toList();
        return new QuizResponse(trackId, topicId, quiz.getQuizId(), questions);
    }

    private static QuizQuestionView toView(QuizQuestion q) {
        return new QuizQuestionView(
                q.getQuizQuestionId(),
                q.getQuestion(),
                List.of(q.getOptionA(), q.getOptionB(), q.getOptionC(), q.getOptionD()),
                q.getSourceFile());
    }
}
