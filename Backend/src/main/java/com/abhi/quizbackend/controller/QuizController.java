package com.abhi.quizbackend.controller;

import com.abhi.quizbackend.models.CheckAnswersRequest;
import com.abhi.quizbackend.models.QuizResponse;
import com.abhi.quizbackend.models.QuizResultResponse;
import com.abhi.quizbackend.security.service.CustomUserDetails;
import com.abhi.quizbackend.service.QuizService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;

    /**
     * The day's quiz, without its answers. Never calls the model: if the day
     * has no quiz yet, the response is 404.
     */
    @GetMapping("/api/tracks/{trackId}/topics/{topicId}/quiz")
    public QuizResponse quiz(
            @PathVariable Long trackId,
            @PathVariable Long topicId,
            @AuthenticationPrincipal CustomUserDetails principal) {

        return quizService.getQuiz(trackId, topicId, principal.getUserId());
    }

    /**
     * Makes the day's quiz now, if it has none. Owner only. Slow (up to a
     * minute) and paid. Until the daily job exists, this is how a quiz is made.
     */
    @PostMapping("/api/tracks/{trackId}/topics/{topicId}/quiz")
    public QuizService.PreparedQuiz prepare(
            @PathVariable Long trackId,
            @PathVariable Long topicId,
            @AuthenticationPrincipal CustomUserDetails principal) {

        return quizService.prepare(trackId, topicId, principal.getUserId());
    }

    /** Scores the answers, and returns the correct answer to every question. */
    @PostMapping("/api/tracks/{trackId}/topics/{topicId}/quiz/answers")
    public QuizResultResponse check(
            @PathVariable Long trackId,
            @PathVariable Long topicId,
            @Valid @RequestBody CheckAnswersRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {

        return quizService.check(trackId, topicId, request, principal.getUserId());
    }
}
