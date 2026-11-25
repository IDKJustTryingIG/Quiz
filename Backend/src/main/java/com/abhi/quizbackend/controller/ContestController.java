package com.abhi.quizbackend.controller;

import com.abhi.quizbackend.models.AnswerRequest;
import com.abhi.quizbackend.models.AttemptResponse;
import com.abhi.quizbackend.models.AttemptResultResponse;
import com.abhi.quizbackend.models.ContestTodayResponse;
import com.abhi.quizbackend.models.LeaderboardResponse;
import com.abhi.quizbackend.security.service.CustomUserDetails;
import com.abhi.quizbackend.service.ContestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ContestController {

    private final ContestService contestService;

    /** Today's contest of the user's track, and the user's attempt in it. */
    @GetMapping("/api/contests/today")
    public ContestTodayResponse today(@AuthenticationPrincipal CustomUserDetails principal) {
        return contestService.today(principal.getUserId());
    }

    /** Starts the user's attempt, or returns the one they already started. */
    @PostMapping("/api/contests/{sessionId}/attempt")
    public AttemptResponse start(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal CustomUserDetails principal) {

        return contestService.start(sessionId, principal.getUserId());
    }

    @GetMapping("/api/contests/{sessionId}/leaderboard")
    public LeaderboardResponse leaderboard(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal CustomUserDetails principal) {

        return contestService.leaderboard(sessionId, principal.getUserId());
    }

    @GetMapping("/api/attempts/{attemptId}")
    public AttemptResponse attempt(
            @PathVariable Long attemptId,
            @AuthenticationPrincipal CustomUserDetails principal) {

        return contestService.attempt(attemptId, principal.getUserId());
    }

    /** Saves one answer. The first answer to a question is final. */
    @PutMapping("/api/attempts/{attemptId}/answers")
    public ResponseEntity<Void> answer(
            @PathVariable Long attemptId,
            @Valid @RequestBody AnswerRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {

        contestService.answer(attemptId, request, principal.getUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/attempts/{attemptId}/submit")
    public AttemptResultResponse submit(
            @PathVariable Long attemptId,
            @AuthenticationPrincipal CustomUserDetails principal) {

        return contestService.submit(attemptId, principal.getUserId());
    }

    /** The score, and after the contest closes, the correct answers. */
    @GetMapping("/api/attempts/{attemptId}/result")
    public AttemptResultResponse result(
            @PathVariable Long attemptId,
            @AuthenticationPrincipal CustomUserDetails principal) {

        return contestService.result(attemptId, principal.getUserId());
    }
}
