package com.abhi.quizbackend.controller;

import com.abhi.quizbackend.models.QuizJobResponse;
import com.abhi.quizbackend.service.EnrollmentService;
import com.abhi.quizbackend.service.QuizJobAdminService;
import com.abhi.quizbackend.service.QuizPlanner;
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

/** The quiz queue, for admins. A date defaults to today in the schedule's time zone. */
@RestController
@RequestMapping("/api/admin/quiz-jobs")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminQuizJobController {

    private final QuizJobAdminService adminService;
    private final QuizPlanner planner;
    private final EnrollmentService enrollmentService;

    @GetMapping
    public List<QuizJobResponse> jobs(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        return adminService.jobsOn(date == null ? enrollmentService.today() : date);
    }

    /**
     * Plans the date now, as the 06:00 and 17:00 runs do. Safe to repeat.
     * The workers start on the queued jobs within a few seconds.
     */
    @PostMapping("/plan")
    public QuizPlanner.PlanResult plan(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        return planner.plan(date == null ? enrollmentService.today() : date);
    }
}
