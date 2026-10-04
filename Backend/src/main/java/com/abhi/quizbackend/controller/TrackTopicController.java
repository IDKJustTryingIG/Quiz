package com.abhi.quizbackend.controller;

import com.abhi.quizbackend.models.AddTopicToTrackRequest;
import com.abhi.quizbackend.models.TrackTopicResponse;
import com.abhi.quizbackend.security.service.CustomUserDetails;
import com.abhi.quizbackend.service.TrackTopicService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tracks/{trackId}/topics")
@RequiredArgsConstructor
public class TrackTopicController {

    private final TrackTopicService trackTopicService;

    @GetMapping
    public ResponseEntity<List<TrackTopicResponse>> getTopics(
            @PathVariable Long trackId,
            @AuthenticationPrincipal CustomUserDetails principal) {

        return ResponseEntity.ok(
                trackTopicService.getTopics(trackId, principal.getUserId())
        );
    }

    @PostMapping("/{topicId}")
    public ResponseEntity<Void> addTopic(
            @PathVariable Long trackId,
            @PathVariable Long topicId,
            @Valid @RequestBody AddTopicToTrackRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {

        trackTopicService.addTopic(
                trackId,
                topicId,
                request.position(),
                principal.getUserId()
        );

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{topicId}")
    public ResponseEntity<Void> removeTopic(
            @PathVariable Long trackId,
            @PathVariable Long topicId,
            @AuthenticationPrincipal CustomUserDetails principal) {

        trackTopicService.removeTopic(trackId, topicId, principal.getUserId());

        return ResponseEntity.noContent().build();
    }
}
