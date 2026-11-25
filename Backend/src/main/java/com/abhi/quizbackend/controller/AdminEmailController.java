package com.abhi.quizbackend.controller;

import com.abhi.quizbackend.email.DailyEmailService;
import com.abhi.quizbackend.models.EmailDeliveryResponse;
import com.abhi.quizbackend.service.EnrollmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** The daily emails, for admins. A date defaults to today in the schedule's time zone. */
@RestController
@RequestMapping("/api/admin/emails")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminEmailController {

    private final DailyEmailService emailService;
    private final EnrollmentService enrollmentService;

    @GetMapping
    public List<EmailDeliveryResponse> deliveries(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        return emailService.deliveriesOn(date == null ? enrollmentService.today() : date);
    }

    /** Plans the date now, as the 06:00 run does. The sender sends within 30 seconds. */
    @PostMapping("/plan")
    public DailyEmailService.PlanResult plan(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        return emailService.plan(date == null ? enrollmentService.today() : date);
    }
}
