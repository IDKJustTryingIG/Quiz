package com.abhi.quizbackend.service;

import com.abhi.quizbackend.entity.TrackVisibility;
import com.abhi.quizbackend.entity.User;
import com.abhi.quizbackend.exception.ContestConflictException;
import com.abhi.quizbackend.exception.QuizLockedException;
import com.abhi.quizbackend.models.AnswerRequest;
import com.abhi.quizbackend.models.AttemptResponse;
import com.abhi.quizbackend.models.AttemptResultResponse;
import com.abhi.quizbackend.models.ContestTodayResponse;
import com.abhi.quizbackend.models.ContestTodayResponse.State;
import com.abhi.quizbackend.models.CreateFullTrackRequest;
import com.abhi.quizbackend.models.CreateFullTrackRequest.TopicDraft;
import com.abhi.quizbackend.models.EditTrackRequest;
import com.abhi.quizbackend.models.EditTrackRequest.DayEdit;
import com.abhi.quizbackend.models.LeaderboardResponse;
import com.abhi.quizbackend.models.QuizQuestionResponse;
import com.abhi.quizbackend.models.TrackDetailResponse;
import com.abhi.quizbackend.properties.ContestProperties;
import com.abhi.quizbackend.properties.FileStorageProperties;
import com.abhi.quizbackend.properties.QuizJobProperties;
import com.abhi.quizbackend.properties.QuizProperties;
import com.abhi.quizbackend.properties.ScheduleProperties;
import com.abhi.quizbackend.repository.QuizGenerationJobRepository;
import com.abhi.quizbackend.repository.QuizSessionRepository;
import com.abhi.quizbackend.repository.UserRepository;
import com.abhi.quizbackend.support.TestClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Plays whole contest evenings against a real JPA stack on H2, with a test
 * clock and a mock model. The fake quizzes have the correct option first.
 *
 * Every test has the same cast, on one two-day track:
 * Asha and Ben are on Day 2, Cai is on Day 1.
 */
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@Import({ContestService.class, ContestSessionService.class, AttemptScorer.class, QuizService.class,
        QuizPlanner.class, QuizJobWorker.class, QuizCacheService.class, TopicMediaService.class,
        EnrollmentService.class, TrackService.class, TrackCreationService.class, TrackEditService.class,
        OrphanCleaner.class, TrackFileStore.class, LocalFileStorageService.class,
        ContestIntegrationTest.Clocks.class})
@EnableConfigurationProperties({FileStorageProperties.class, QuizProperties.class, QuizJobProperties.class,
        ScheduleProperties.class, ContestProperties.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ContestIntegrationTest {

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    /** 13 Oct 2026, 10:00 in India. */
    private static final Instant MORNING = Instant.parse("2026-10-13T04:30:00Z");

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("app.file-storage.location", ContestIntegrationTest::createStorage);
    }

    @TestConfiguration
    static class Clocks {
        @Bean
        TestClock clock() {
            return new TestClock(MORNING);
        }
    }

    @MockitoBean QuizGeneratorService quizGeneratorService;

    @Autowired ContestService contestService;
    @Autowired ContestSessionService contestSessionService;
    @Autowired QuizService quizService;
    @Autowired QuizPlanner planner;
    @Autowired QuizJobWorker worker;
    @Autowired EnrollmentService enrollmentService;
    @Autowired TrackService trackService;
    @Autowired TrackCreationService trackCreationService;
    @Autowired TrackEditService trackEditService;
    @Autowired QuizGenerationJobRepository jobRepository;
    @Autowired QuizSessionRepository sessionRepository;
    @Autowired UserRepository userRepository;
    @Autowired TestClock clock;

    private final AtomicInteger quizzesMade = new AtomicInteger();
    private Long ownerId;
    private Long trackId;
    private Long asha;
    private Long ben;
    private Long cai;

    @BeforeEach
    void setUp() {
        // Each test plays its own evening, on its own date.
        clock.plusDays(10);
        clock.set(at(LocalTime.of(10, 0)));
        jobRepository.deleteAll();
        doAnswer(call -> fakeQuiz(quizzesMade.incrementAndGet())).when(quizGeneratorService).generate(any());

        ownerId = user("owner");
        trackId = track();
        asha = user("asha");
        ben = user("ben");
        cai = user("cai");
        enrollmentService.enroll(trackId, asha, false);
        enrollmentService.enroll(trackId, ben, false);
        clock.plusDays(1);
        enrollmentService.enroll(trackId, cai, false);
        clock.plusDays(1);

        // The 06:00 run of the contest day, then the workers.
        planner.plan(enrollmentService.today());
        while (worker.processNext()) {
            // The next job.
        }
    }

    @Test
    void beforeSixTheContestIsUpcomingAndCannotStart() {
        ContestTodayResponse today = contestService.today(asha);

        assertThat(today.state()).isEqualTo(State.UPCOMING);
        assertThat(today.dayNumber()).isEqualTo(2);
        assertThat(today.quizReady()).isTrue();
        assertThat(today.canStart()).isFalse();
        assertThatThrownBy(() -> contestService.start(today.sessionId(), asha))
                .isInstanceOf(ContestConflictException.class);
    }

    @Test
    void anEveningEndsWithOneRankingForBothDays() {
        clock.set(at(LocalTime.of(18, 1)));
        Long sessionId = contestService.today(asha).sessionId();

        // Asha answers all 10 right in 2 minutes.
        AttemptResponse ashaAttempt = contestService.start(sessionId, asha);
        answerAll(ashaAttempt, asha, 10);
        clock.set(at(LocalTime.of(18, 3)));
        AttemptResultResponse ashaResult = contestService.submit(ashaAttempt.attemptId(), asha);

        // Cai, on Day 1, answers 6 right and submits.
        AttemptResponse caiAttempt = contestService.start(sessionId, cai);
        answerAll(caiAttempt, cai, 6);
        clock.set(at(LocalTime.of(18, 4)));
        contestService.submit(caiAttempt.attemptId(), cai);

        // Ben answers 3 right and never submits.
        AttemptResponse benAttempt = contestService.start(sessionId, ben);
        answerAll(benAttempt, ben, 3);

        assertThat(caiAttempt.questions().get(0).question()).isNotEqualTo(ashaAttempt.questions().get(0).question());
        assertThat(ashaResult.score()).isEqualTo(10);
        assertThat(ashaResult.revealed()).isFalse();
        assertThat(ashaResult.results()).isEmpty();

        LeaderboardResponse live = contestService.leaderboard(sessionId, asha);
        assertThat(live.isFinal()).isFalse();
        assertThat(live.entries()).extracting(LeaderboardResponse.Entry::username, LeaderboardResponse.Entry::rank)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(username(asha), 1),
                        org.assertj.core.groups.Tuple.tuple(username(cai), 2),
                        org.assertj.core.groups.Tuple.tuple(username(ben), null));
        assertThat(live.entries().get(0).you()).isTrue();

        clock.set(at(LocalTime.of(18, 16)));
        LeaderboardResponse last = contestService.leaderboard(sessionId, asha);
        AttemptResultResponse revealed = contestService.result(ashaAttempt.attemptId(), asha);

        assertThat(last.isFinal()).isTrue();
        assertThat(last.entries()).extracting(LeaderboardResponse.Entry::username, LeaderboardResponse.Entry::rank,
                        LeaderboardResponse.Entry::status, LeaderboardResponse.Entry::score)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(username(asha), 1, "SUBMITTED", 10),
                        org.assertj.core.groups.Tuple.tuple(username(cai), 2, "SUBMITTED", 6),
                        org.assertj.core.groups.Tuple.tuple(username(ben), 3, "EXPIRED", 3));
        assertThat(revealed.revealed()).isTrue();
        assertThat(revealed.results()).hasSize(10).allSatisfy(r -> assertThat(r.correctIndex()).isZero());
    }

    @Test
    void aUserCannotStartAfterTheStartWindow() {
        clock.set(at(LocalTime.of(18, 6)));
        ContestTodayResponse today = contestService.today(asha);

        assertThat(today.state()).isEqualTo(State.OPEN);
        assertThat(today.canStart()).isFalse();
        assertThatThrownBy(() -> contestService.start(today.sessionId(), asha))
                .isInstanceOf(ContestConflictException.class);
    }

    @Test
    void theFirstAnswerIsFinalAndTheDeadlineHolds() {
        clock.set(at(LocalTime.of(18, 2)));
        AttemptResponse attempt = contestService.start(contestService.today(asha).sessionId(), asha);
        Long first = attempt.questions().get(0).questionId();
        contestService.answer(attempt.attemptId(), new AnswerRequest(first, 1), asha);

        assertThatThrownBy(() -> contestService.answer(attempt.attemptId(), new AnswerRequest(first, 0), asha))
                .isInstanceOf(ContestConflictException.class);

        // 10 minutes after the start.
        clock.set(at(LocalTime.of(18, 12)));
        Long second = attempt.questions().get(1).questionId();
        assertThatThrownBy(() -> contestService.answer(attempt.attemptId(), new AnswerRequest(second, 0), asha))
                .isInstanceOf(ContestConflictException.class);
        assertThat(contestService.submit(attempt.attemptId(), asha).status()).isEqualTo("EXPIRED");
    }

    @Test
    void anotherUserCannotSeeAnAttempt() {
        clock.set(at(LocalTime.of(18, 1)));
        AttemptResponse attempt = contestService.start(contestService.today(asha).sessionId(), asha);

        assertThatThrownBy(() -> contestService.attempt(attempt.attemptId(), ben))
                .isInstanceOf(com.abhi.quizbackend.exception.ResourceNotFoundException.class);
    }

    @Test
    void practiceIsLockedUntilTheContestCloses() {
        Long day2 = detail().days().get(1).topicId();

        assertThatThrownBy(() -> quizService.getQuiz(trackId, day2, ownerId))
                .isInstanceOf(QuizLockedException.class);

        clock.set(at(LocalTime.of(18, 16)));
        assertThat(quizService.getQuiz(trackId, day2, ownerId).questions()).hasSize(10);
    }

    @Test
    void aFileChangeBeforeTheContestGetsANewQuizButNotAfterItOpens() {
        // 17:00: the owner changed Day 2 during the day. The evening run makes a new quiz.
        clock.set(at(LocalTime.of(17, 0)));
        addPdfToDay(1);
        planner.plan(enrollmentService.today());
        while (worker.processNext()) {
            // The next job.
        }
        clock.set(at(LocalTime.of(18, 1)));
        AttemptResponse attempt = contestService.start(contestService.today(asha).sessionId(), asha);
        assertThat(attempt.questions().get(0).question()).endsWith("of quiz 3");

        // 18:04: another change, and the owner makes a quiz by hand. The contest keeps its quiz.
        clock.set(at(LocalTime.of(18, 4)));
        addPdfToDay(1);
        quizService.prepare(trackId, detail().days().get(1).topicId(), ownerId);
        AttemptResponse benAttempt = contestService.start(contestService.today(ben).sessionId(), ben);

        assertThat(benAttempt.questions().get(0).question()).endsWith("of quiz 3");
        // The pinned quiz was not deleted, so Asha's attempt still loads.
        assertThat(contestService.attempt(attempt.attemptId(), asha).questions()).hasSize(10);
    }

    // ---------- helpers ----------

    /** Answers every question: the first `right` ones with the correct option, the rest wrong. */
    private void answerAll(AttemptResponse attempt, Long userId, int right) {
        for (int i = 0; i < attempt.questions().size(); i++) {
            contestService.answer(attempt.attemptId(),
                    new AnswerRequest(attempt.questions().get(i).questionId(), i < right ? 0 : 1), userId);
        }
    }

    private Instant at(LocalTime time) {
        return enrollmentService.today().atTime(time).atZone(INDIA).toInstant();
    }

    private String username(Long userId) {
        return userRepository.findById(userId).orElseThrow().getUsername();
    }

    private Long track() {
        List<TopicDraft> topics = List.of(new TopicDraft("Arrays"), new TopicDraft("Strings"));
        List<List<MultipartFile>> files = List.of(List.of(pdf("day1.pdf")), List.of(pdf("day2.pdf")));
        return trackCreationService.create(
                new CreateFullTrackRequest("Track " + UUID.randomUUID(), null, TrackVisibility.PUBLIC, topics),
                files, ownerId).trackId();
    }

    private TrackDetailResponse detail() {
        return trackService.getDetailBySlug(trackService.getById(trackId, ownerId).slug(), ownerId);
    }

    private void addPdfToDay(int position) {
        List<TrackDetailResponse.Day> days = detail().days();
        List<DayEdit> edits = days.stream()
                .map(d -> new DayEdit(d.topicId(), d.topicName(), d.files().stream().map(f -> f.fileId()).toList()))
                .toList();
        List<List<MultipartFile>> newFiles = IntStream.range(0, days.size())
                .mapToObj(i -> i == position ? List.<MultipartFile>of(pdf("extra-" + UUID.randomUUID() + ".pdf")) : List.<MultipartFile>of())
                .toList();
        trackEditService.edit(trackId, new EditTrackRequest(null, TrackVisibility.PUBLIC, edits), newFiles, ownerId);
    }

    private static List<QuizQuestionResponse> fakeQuiz(int quiz) {
        return IntStream.rangeClosed(1, 10)
                .mapToObj(n -> new QuizQuestionResponse("Q" + n + " of quiz " + quiz,
                        List.of("right", "wrong a", "wrong b", "wrong c"), 0, "Because.", "notes.pdf"))
                .toList();
    }

    private static MockMultipartFile pdf(String name) {
        return new MockMultipartFile("file", name, "application/pdf", ("%PDF " + name + UUID.randomUUID()).getBytes());
    }

    private Long user(String name) {
        User user = new User();
        String unique = name + UUID.randomUUID().toString().substring(0, 6);
        user.setEmail(unique + "@example.com");
        user.setUsername(unique);
        return userRepository.save(user).getUserId();
    }

    private static String createStorage() {
        try {
            return Files.createTempDirectory("contest-test").toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
