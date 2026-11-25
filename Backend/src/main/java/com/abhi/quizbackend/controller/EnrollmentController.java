package com.abhi.quizbackend.controller;

import com.abhi.quizbackend.models.EnrollmentResponse;
import com.abhi.quizbackend.security.service.CustomUserDetails;
import com.abhi.quizbackend.service.EnrollmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    /**
     * Enrolls the user in the track. Day 1 is tomorrow.
     *
     * Fails with 409 if the user follows another track, unless switch=true:
     * then they leave that track in the same step.
     */
    @PostMapping("/api/tracks/{trackId}/enrollment")
    public ResponseEntity<EnrollmentResponse> enroll(
            @PathVariable Long trackId,
            @RequestParam(name = "switch", defaultValue = "false") boolean switchTrack,
            @AuthenticationPrincipal CustomUserDetails principal) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(enrollmentService.enroll(trackId, principal.getUserId(), switchTrack));
    }

    public record EmailSetting(boolean enabled) {
    }

    /** Turns the 06:00 email on or off. */
    @PutMapping("/api/tracks/{trackId}/enrollment/email")
    public EnrollmentResponse setEmail(
            @PathVariable Long trackId,
            @RequestBody EmailSetting setting,
            @AuthenticationPrincipal CustomUserDetails principal) {

        return enrollmentService.setEmailOptIn(trackId, principal.getUserId(), setting.enabled());
    }

    @DeleteMapping("/api/tracks/{trackId}/enrollment")
    public ResponseEntity<Void> leave(
            @PathVariable Long trackId,
            @AuthenticationPrincipal CustomUserDetails principal) {

        enrollmentService.leave(trackId, principal.getUserId());

        return ResponseEntity.noContent().build();
    }

    /** The track the user follows and today's topic, or 204 if they follow none. */
    @GetMapping("/api/enrollments/current")
    public ResponseEntity<EnrollmentResponse> current(
            @AuthenticationPrincipal CustomUserDetails principal) {

        return enrollmentService.current(principal.getUserId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
