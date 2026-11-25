package com.abhi.quizbackend.service;

import com.abhi.quizbackend.entity.QuizGenerationJob;
import com.abhi.quizbackend.entity.QuizJobStatus;
import com.abhi.quizbackend.entity.TrackVisibility;
import com.abhi.quizbackend.entity.User;
import com.abhi.quizbackend.exception.QuizGenerationException;
import com.abhi.quizbackend.exception.QuizSourceException;
import com.abhi.quizbackend.models.CreateFullTrackRequest;
import com.abhi.quizbackend.models.CreateFullTrackRequest.TopicDraft;
import com.abhi.quizbackend.models.EditTrackRequest;
import com.abhi.quizbackend.models.EditTrackRequest.DayEdit;
import com.abhi.quizbackend.models.QuizQuestionResponse;
import com.abhi.quizbackend.models.TrackDetailResponse;
import com.abhi.quizbackend.properties.ContestProperties;
import com.abhi.quizbackend.properties.FileStorageProperties;
import com.abhi.quizbackend.properties.QuizJobProperties;
import com.abhi.quizbackend.properties.QuizProperties;
import com.abhi.quizbackend.properties.ScheduleProperties;
import com.abhi.quizbackend.repository.QuizGenerationJobRepository;
import com.abhi.quizbackend.repository.UserRepository;
import com.abhi.quizbackend.support.TestClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Runs the planner and the worker against a real JPA stack on H2, with the
 * model replaced by a mock and a test clock. The worker runs on the test's
 * thread: processNext() is what each worker thread calls in a loop.
 *
 * H2 has no SKIP LOCKED, so two workers that race for one job are not tested here.
 */
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@Import({QuizPlanner.class, QuizJobWorker.class, ContestSessionService.class, AttemptScorer.class, QuizCacheService.class, TopicMediaService.class,
        EnrollmentService.class, TrackService.class, TrackCreationService.class, TrackEditService.class,
        OrphanCleaner.class, TrackFileStore.class, LocalFileStorageService.class,
        QuizJobIntegrationTest.Clocks.class})
@EnableConfigurationProperties({FileStorageProperties.class, QuizProperties.class,
        QuizJobProperties.class, ScheduleProperties.class, ContestProperties.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class QuizJobIntegrationTest {

    /** 13 Oct 2026, 10:00 in India. */
    private static final Instant MORNING = Instant.parse("2026-10-13T04:30:00Z");

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("app.file-storage.location", QuizJobIntegrationTest::createStorage);
    }

    @TestConfiguration
    static class Clocks {
        @Bean
        TestClock clock() {
            return new TestClock(MORNING);
        }
    }

    @MockitoBean QuizGeneratorService quizGeneratorService;

    @Autowired QuizPlanner planner;
    @Autowired QuizJobWorker worker;
    @Autowired QuizCacheService quizCacheService;
    @Autowired EnrollmentService enrollmentService;
    @Autowired TrackService trackService;
    @Autowired TrackCreationService trackCreationService;
    @Autowired TrackEditService trackEditService;
    @Autowired QuizGenerationJobRepository jobRepository;
    @Autowired UserRepository userRepository;
    @Autowired TestClock clock;

    private Long ownerId;

    @BeforeEach
    void setUp() {
        // Each test starts on its own day, so the jobs of other tests are on other dates.
        clock.plusDays(10);
        // The queue is shared, as in the app. Each test starts with an empty one.
        jobRepository.deleteAll();
        ownerId = user();
        doAnswer(call -> {
            Media[] media = call.getArgument(0);
            // What the real generator does when the day has nothing it can read.
            if (media.length == 0) throw new QuizSourceException("This topic has no files to make a quiz from.");
            return fakeQuiz();
        }).when(quizGeneratorService).generate(any());
    }

    @Test
    void plansOneJobPerTopicThatAnEnrolledUserNeedsToday() {
        Long trackId = track(3);
        // Two users on Day 2, one user on Day 1, one user who starts tomorrow.
        enroll(user(), trackId);
        enroll(user(), trackId);
        clock.plusDays(1);
        enroll(user(), trackId);
        clock.plusDays(1);
        enroll(user(), trackId);
        LocalDate today = enrollmentService.today();

        QuizPlanner.PlanResult result = planner.plan(today);

        assertThat(result.needed()).isEqualTo(2);
        assertThat(result.queued()).isEqualTo(2);
        assertThat(jobsOn(today)).extracting(job -> job.getTopic().getName())
                .containsExactlyInAnyOrder("Topic 1", "Topic 2");
        verify(quizGeneratorService, never()).generate(any());
    }

    @Test
    void planningTwiceAddsNothing() {
        Long trackId = track(2);
        enroll(user(), trackId);
        clock.plusDays(1);

        planner.plan(today());
        QuizPlanner.PlanResult again = planner.plan(today());

        assertThat(again.queued()).isZero();
        assertThat(jobsOn(today())).hasSize(1);
    }

    @Test
    void theWorkerMakesEachQueuedQuizOnce() {
        Long first = track(2);
        Long second = track(2);
        enroll(user(), first);
        enroll(user(), second);
        clock.plusDays(1);
        planner.plan(today());

        int processed = drain();

        assertThat(processed).isEqualTo(2);
        assertThat(jobsOn(today())).allSatisfy(job -> {
            assertThat(job.getStatus()).isEqualTo(QuizJobStatus.DONE);
            assertThat(job.getQuizId()).isNotNull();
            assertThat(job.getAttempts()).isEqualTo(1);
        });
        verify(quizGeneratorService, times(2)).generate(any());
    }

    @Test
    void aTopicThatAlreadyHasAQuizCostsNothing() {
        Long trackId = track(1);
        enroll(user(), trackId);
        clock.plusDays(1);
        quizCacheService.prepare(trackId, dayTopicId(trackId, 0));

        QuizPlanner.PlanResult result = planner.plan(today());

        assertThat(result.alreadyReady()).isEqualTo(1);
        assertThat(jobsOn(today()).get(0).getStatus()).isEqualTo(QuizJobStatus.DONE);
        verify(quizGeneratorService, times(1)).generate(any());
    }

    @Test
    void aModelErrorIsRetriedAfterADelay() {
        Long trackId = track(1);
        enroll(user(), trackId);
        clock.plusDays(1);
        planner.plan(today());
        doThrow(new QuizGenerationException("The model returned no quiz."))
                .doAnswer(call -> fakeQuiz())
                .when(quizGeneratorService).generate(any());

        assertThat(worker.processNext()).isTrue();
        QuizGenerationJob waiting = jobsOn(today()).get(0);
        assertThat(waiting.getStatus()).isEqualTo(QuizJobStatus.PENDING);
        assertThat(waiting.getLastError()).contains("The model returned no quiz.");
        // Not before the delay is over.
        assertThat(worker.processNext()).isFalse();

        clock.plus(Duration.ofMinutes(1));
        assertThat(worker.processNext()).isTrue();

        QuizGenerationJob done = jobsOn(today()).get(0);
        assertThat(done.getStatus()).isEqualTo(QuizJobStatus.DONE);
        assertThat(done.getAttempts()).isEqualTo(2);
    }

    @Test
    void aJobFailsAfterTheLastAttempt() {
        Long trackId = track(1);
        enroll(user(), trackId);
        clock.plusDays(1);
        planner.plan(today());
        doThrow(new QuizGenerationException("No quiz.")).when(quizGeneratorService).generate(any());

        for (int i = 0; i < 10; i++) {
            worker.processNext();
            clock.plus(Duration.ofMinutes(30));
        }

        QuizGenerationJob job = jobsOn(today()).get(0);
        assertThat(job.getStatus()).isEqualTo(QuizJobStatus.FAILED);
        assertThat(job.getAttempts()).isEqualTo(4);
        verify(quizGeneratorService, times(4)).generate(any());
    }

    @Test
    void aDayWithNoReadableFilesFailsAtOnce() {
        Long trackId = track(1, new MockMultipartFile("file", "slides.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx".getBytes()));
        enroll(user(), trackId);
        clock.plusDays(1);
        planner.plan(today());

        worker.processNext();

        QuizGenerationJob job = jobsOn(today()).get(0);
        assertThat(job.getStatus()).isEqualTo(QuizJobStatus.FAILED);
        assertThat(job.getAttempts()).isEqualTo(1);
        assertThat(job.getLastError()).contains("no files");
    }

    @Test
    void theEveningRunQueuesAgainWhenTheFilesChanged() {
        Long trackId = track(1);
        enroll(user(), trackId);
        clock.plusDays(1);
        planner.plan(today());
        drain();

        addPdf(trackId);
        QuizPlanner.PlanResult evening = planner.plan(today());
        drain();

        assertThat(evening.requeued()).isEqualTo(1);
        assertThat(jobsOn(today()).get(0).getStatus()).isEqualTo(QuizJobStatus.DONE);
        verify(quizGeneratorService, times(2)).generate(any());
    }

    @Test
    void aJobWhoseWorkerStoppedGoesBackInTheQueue() {
        Long trackId = track(1);
        enroll(user(), trackId);
        clock.plusDays(1);
        planner.plan(today());
        QuizGenerationJob job = jobsOn(today()).get(0);
        job.setStatus(QuizJobStatus.RUNNING);
        job.setAttempts(1);
        job.setLockedAt(clock.instant().minus(Duration.ofMinutes(11)));
        jobRepository.save(job);

        worker.releaseStale();

        assertThat(jobsOn(today()).get(0).getStatus()).isEqualTo(QuizJobStatus.PENDING);
        assertThat(worker.processNext()).isTrue();
        assertThat(jobsOn(today()).get(0).getStatus()).isEqualTo(QuizJobStatus.DONE);
    }

    // ---------- helpers ----------

    private LocalDate today() {
        return enrollmentService.today();
    }

    private int drain() {
        int processed = 0;
        while (worker.processNext()) {
            processed++;
        }
        return processed;
    }

    private List<QuizGenerationJob> jobsOn(LocalDate date) {
        return jobRepository.findByRunDateOrderByJobIdAsc(date);
    }

    private void enroll(Long userId, Long trackId) {
        enrollmentService.enroll(trackId, userId, false);
    }

    private Long track(int days) {
        return track(days, null);
    }

    /** A public track. Each day has its own PDF, or the given file. */
    private Long track(int days, MultipartFile file) {
        List<TopicDraft> topics = IntStream.rangeClosed(1, days)
                .mapToObj(n -> new TopicDraft("Topic " + n))
                .toList();
        List<List<MultipartFile>> files = IntStream.rangeClosed(1, days)
                .mapToObj(n -> List.<MultipartFile>of(file != null ? file : pdf("day" + n + ".pdf")))
                .toList();
        return trackCreationService.create(
                new CreateFullTrackRequest("Track " + UUID.randomUUID(), null, TrackVisibility.PUBLIC, topics),
                files,
                ownerId).trackId();
    }

    private Long dayTopicId(Long trackId, int position) {
        return detail(trackId).days().get(position).topicId();
    }

    private TrackDetailResponse detail(Long trackId) {
        return trackService.getDetailBySlug(trackService.getById(trackId, ownerId).slug(), ownerId);
    }

    /** Adds a PDF to Day 1 of a one-day track. */
    private void addPdf(Long trackId) {
        TrackDetailResponse.Day day = detail(trackId).days().get(0);
        trackEditService.edit(trackId,
                new EditTrackRequest(null, TrackVisibility.PUBLIC,
                        List.of(new DayEdit(day.topicId(), day.topicName(),
                                day.files().stream().map(file -> file.fileId()).toList()))),
                List.of(List.of(pdf("more.pdf"))),
                ownerId);
    }

    private static List<QuizQuestionResponse> fakeQuiz() {
        return IntStream.rangeClosed(1, 10)
                .mapToObj(n -> new QuizQuestionResponse("Q" + n,
                        List.of("right", "wrong a", "wrong b", "wrong c"), 0, "Because.", "notes.pdf"))
                .toList();
    }

    private static MockMultipartFile pdf(String name) {
        return new MockMultipartFile("file", name, "application/pdf", ("%PDF " + name + UUID.randomUUID()).getBytes());
    }

    private Long user() {
        User user = new User();
        String unique = "job" + UUID.randomUUID().toString().substring(0, 8);
        user.setEmail(unique + "@example.com");
        user.setUsername(unique);
        return userRepository.save(user).getUserId();
    }

    private static String createStorage() {
        try {
            return Files.createTempDirectory("quiz-job-test").toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
