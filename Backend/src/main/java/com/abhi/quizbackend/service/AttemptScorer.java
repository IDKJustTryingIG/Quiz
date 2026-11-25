package com.abhi.quizbackend.service;

import com.abhi.quizbackend.entity.AttemptStatus;
import com.abhi.quizbackend.entity.QuizAnswer;
import com.abhi.quizbackend.entity.QuizAttempt;
import com.abhi.quizbackend.entity.QuizQuestion;
import com.abhi.quizbackend.repository.QuizRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Scores an attempt from the answers it saved: 1 mark for each correct answer. */
@Component
@RequiredArgsConstructor
public class AttemptScorer {

    private final QuizRepository quizRepository;

    /**
     * @param end    the submission time, or the deadline if the time ran out
     * @param status SUBMITTED or EXPIRED
     */
    public void score(QuizAttempt attempt, Instant end, AttemptStatus status) {
        Map<Long, QuizQuestion> questions = attempt.getQuizId() == null
                ? Map.of()
                : quizRepository.findWithQuestionsByQuizId(attempt.getQuizId())
                        .map(quiz -> quiz.getQuestions().stream()
                                .collect(Collectors.toMap(QuizQuestion::getQuizQuestionId, Function.identity())))
                        .orElse(Map.of());

        int score = 0;
        for (QuizAnswer answer : attempt.getAnswers()) {
            QuizQuestion question = questions.get(answer.getQuestionId());
            boolean correct = question != null && question.getCorrectIndex().equals(answer.getChosenIndex());
            answer.setCorrect(correct);
            if (correct) {
                score++;
            }
        }

        attempt.setScore(score);
        attempt.setStatus(status);
        attempt.setSubmittedAt(end);
        attempt.setDurationMs(Duration.between(attempt.getStartedAt(), end).toMillis());
    }
}
