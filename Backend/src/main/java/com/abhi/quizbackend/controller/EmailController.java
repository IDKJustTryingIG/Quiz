package com.abhi.quizbackend.controller;

import com.abhi.quizbackend.service.EnrollmentService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** "Stop daily emails". Open without a sign-in: the token in the link is the proof. */
@RestController
@RequiredArgsConstructor
public class EmailController {

    private final EnrollmentService enrollmentService;

    /**
     * Turns the daily email off. The frontend's unsubscribe page and the mail
     * app's own Unsubscribe button (RFC 8058, a form POST) both call this.
     *
     * Always 204, so the response does not say whether a token exists.
     */
    @PostMapping("/api/email/unsubscribe")
    public ResponseEntity<Void> unsubscribe(@RequestParam @NotBlank @Size(max = 64) String token) {
        enrollmentService.unsubscribe(token);
        return ResponseEntity.noContent().build();
    }
}
