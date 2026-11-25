package com.abhi.quizbackend.service;

import com.abhi.quizbackend.models.QuizJobResponse;
import com.abhi.quizbackend.repository.QuizGenerationJobRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** What the admin page reads about the quiz queue. */
@Service
@RequiredArgsConstructor
public class QuizJobAdminService {

    private final QuizGenerationJobRepository jobRepository;

    @Transactional(readOnly = true)
    public List<QuizJobResponse> jobsOn(LocalDate date) {
        return jobRepository.findByRunDateOrderByJobIdAsc(date).stream()
                .map(job -> new QuizJobResponse(
                        job.getJobId(),
                        job.getRunDate(),
                        job.getTrack().getTrackId(),
                        job.getTrack().getName(),
                        job.getTopic().getTopicId(),
                        job.getTopic().getName(),
                        job.getStatus(),
                        job.getAttempts(),
                        job.getNextAttemptAt(),
                        job.getLastError(),
                        job.getQuizId(),
                        job.getFinishedAt()))
                .toList();
    }
}
