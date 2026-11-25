package com.abhi.quizbackend.service;

import com.abhi.quizbackend.entity.TrackVisibility;
import com.abhi.quizbackend.entity.User;
import com.abhi.quizbackend.models.CreateFullTrackRequest;
import com.abhi.quizbackend.models.CreateFullTrackRequest.TopicDraft;
import com.abhi.quizbackend.models.EditTrackRequest;
import com.abhi.quizbackend.models.EditTrackRequest.DayEdit;
import com.abhi.quizbackend.exception.QuizNotReadyException;
import com.abhi.quizbackend.exception.ResourceNotFoundException;
import com.abhi.quizbackend.models.CheckAnswersRequest;
import com.abhi.quizbackend.models.CheckAnswersRequest.Answer;
import com.abhi.quizbackend.models.QuizQuestionResponse;
import com.abhi.quizbackend.models.QuizResponse;
import com.abhi.quizbackend.models.QuizResultResponse;
import com.abhi.quizbackend.models.TrackResponse;
import com.abhi.quizbackend.properties.FileStorageProperties;
import com.abhi.quizbackend.properties.QuizProperties;
import com.abhi.quizbackend.repository.QuizRepository;
import com.abhi.quizbackend.repository.TrackTopicRepository;
import com.abhi.quizbackend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Runs the quiz cache and the quiz service against a real JPA stack on H2.
 * The model is replaced by a mock, so the tests count model calls instead of
 * paying for them.
 */
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@Import({QuizService.class, QuizCacheService.class, TopicMediaService.class, TrackService.class,
        TrackCreationService.class, TrackEditService.class, OrphanCleaner.class,
        TrackFileStore.class, LocalFileStorageService.class, QuizCacheIntegrationTest.Clocks.class})
@EnableConfigurationProperties({FileStorageProperties.class, QuizProperties.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class QuizCacheIntegrationTest {

    private static final Path STORAGE = createStorage();

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("app.file-storage.location", STORAGE::toString);
    }

    @TestConfiguration
    static class Clocks {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }

    @MockitoBean QuizGeneratorService quizGeneratorService;

    @Autowired QuizService quizService;
    @Autowired TrackService trackService;
    @Autowired TrackCreationService trackCreationService;
    @Autowired TrackEditService trackEditService;
    @Autowired QuizRepository quizRepository;
    @Autowired TrackTopicRepository trackTopicRepository;
    @Autowired UserRepository userRepository;

    private final AtomicInteger generated = new AtomicInteger();
    private Long ownerId;

    @BeforeEach
    void setUp() {
        ownerId = user().getUserId();
        // Each model call returns a quiz whose questions name the call, e.g. "Q3 of quiz 2".
        when(quizGeneratorService.generate(any())).thenAnswer(call -> fakeQuiz(generated.incrementAndGet()));
    }

    @Test
    void openingAQuizNeverCallsTheModel() {
        Day day = trackWithOneDay(pdf("notes.pdf"));

        assertThatThrownBy(() -> quizService.getQuiz(day.trackId(), day.topicId(), ownerId))
                .isInstanceOf(QuizNotReadyException.class);
        verify(quizGeneratorService, never()).generate(any());
    }

    @Test
    void aPreparedQuizIsServedWithoutCallingTheModelAgain() {
        Day day = trackWithOneDay(pdf("notes.pdf"));

        QuizService.PreparedQuiz prepared = quizService.prepare(day.trackId(), day.topicId(), ownerId);
        QuizService.PreparedQuiz again = quizService.prepare(day.trackId(), day.topicId(), ownerId);
        QuizResponse opened = quizService.getQuiz(day.trackId(), day.topicId(), ownerId);

        verify(quizGeneratorService, times(1)).generate(any());
        assertThat(again.quizId()).isEqualTo(prepared.quizId());
        assertThat(opened.quizId()).isEqualTo(prepared.quizId());
        assertThat(opened.questions()).hasSize(10);
        assertThat(opened.questions().get(0).options()).hasSize(4);
    }

    @Test
    void addingAFileTheModelReadsNeedsANewQuizAndDeletesTheOldOne() {
        Day day = trackWithOneDay(pdf("notes.pdf"));
        quizService.prepare(day.trackId(), day.topicId(), ownerId);

        addFiles(day, pdf("more.pdf"));
        assertThatThrownBy(() -> quizService.getQuiz(day.trackId(), day.topicId(), ownerId))
                .isInstanceOf(QuizNotReadyException.class);
        quizService.prepare(day.trackId(), day.topicId(), ownerId);
        QuizResponse after = quizService.getQuiz(day.trackId(), day.topicId(), ownerId);

        verify(quizGeneratorService, times(2)).generate(any());
        assertThat(after.questions().get(0).question()).endsWith("of quiz 2");
        assertThat(quizzesOf(day)).isEqualTo(1);
    }

    @Test
    void addingAFileTheModelCannotReadKeepsTheQuiz() {
        Day day = trackWithOneDay(pdf("notes.pdf"));
        QuizService.PreparedQuiz before = quizService.prepare(day.trackId(), day.topicId(), ownerId);

        addFiles(day, new MockMultipartFile("file", "slides.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx".getBytes()));

        assertThat(quizService.getQuiz(day.trackId(), day.topicId(), ownerId).quizId()).isEqualTo(before.quizId());
        verify(quizGeneratorService, times(1)).generate(any());
    }

    @Test
    void deletingTheTrackDeletesItsQuiz() {
        Day day = trackWithOneDay(pdf("notes.pdf"));
        quizService.prepare(day.trackId(), day.topicId(), ownerId);
        assertThat(quizzesOf(day)).isEqualTo(1);

        trackService.delete(day.trackId(), ownerId);

        assertThat(quizzesOf(day)).isZero();
    }

    @Test
    void theServerScoresTheAnswers() {
        Day day = trackWithOneDay(pdf("notes.pdf"));
        quizService.prepare(day.trackId(), day.topicId(), ownerId);
        QuizResponse quiz = quizService.getQuiz(day.trackId(), day.topicId(), ownerId);
        // The fake quiz has the correct option first. Answer 7 right, 2 wrong, and 1 not at all.
        List<Answer> answers = IntStream.range(0, 9)
                .mapToObj(i -> new Answer(quiz.questions().get(i).questionId(), i < 7 ? 0 : 2))
                .toList();

        QuizResultResponse result = quizService.check(day.trackId(), day.topicId(),
                new CheckAnswersRequest(quiz.quizId(), answers), ownerId);

        assertThat(result.score()).isEqualTo(7);
        assertThat(result.total()).isEqualTo(10);
        assertThat(result.results().get(9).chosenIndex()).isNull();
        assertThat(result.results().get(9).correct()).isFalse();
        assertThat(result.results().get(0).correctIndex()).isZero();
        assertThat(result.results().get(0).explanation()).isEqualTo("Because.");
    }

    @Test
    void answersToAQuizOfAnotherDayAreRefused() {
        Day first = trackWithOneDay(pdf("a.pdf"));
        Day second = trackWithOneDay(pdf("b.pdf"));
        QuizService.PreparedQuiz other = quizService.prepare(second.trackId(), second.topicId(), ownerId);

        assertThatThrownBy(() -> quizService.check(first.trackId(), first.topicId(),
                new CheckAnswersRequest(other.quizId(), List.of()), ownerId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void onlyTheOwnerCanPrepareAQuiz() {
        Day day = trackWithOneDay(pdf("notes.pdf"), TrackVisibility.PUBLIC);
        Long otherUserId = user().getUserId();

        assertThatThrownBy(() -> quizService.prepare(day.trackId(), day.topicId(), otherUserId))
                .isInstanceOf(AccessDeniedException.class);
        verify(quizGeneratorService, never()).generate(any());
    }

    @Test
    void aPrivateTracksQuizIsHiddenFromOtherUsers() {
        Day day = trackWithOneDay(pdf("notes.pdf"));
        quizService.prepare(day.trackId(), day.topicId(), ownerId);
        Long otherUserId = user().getUserId();

        assertThatThrownBy(() -> quizService.getQuiz(day.trackId(), day.topicId(), otherUserId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- helpers ----------

    private record Day(Long trackId, Long topicId) {
    }

    private Day trackWithOneDay(MultipartFile file) {
        return trackWithOneDay(file, TrackVisibility.PRIVATE);
    }

    private Day trackWithOneDay(MultipartFile file, TrackVisibility visibility) {
        TrackResponse track = trackCreationService.create(
                new CreateFullTrackRequest("Track " + UUID.randomUUID(), null, visibility,
                        List.of(new TopicDraft("Day one"))),
                List.of(List.of(file)),
                ownerId);
        Long topicId = trackTopicRepository.findByTrackTrackIdOrderByPositionAsc(track.trackId())
                .get(0).getTopic().getTopicId();
        return new Day(track.trackId(), topicId);
    }

    /** Keeps the day's current files and adds these ones. */
    private void addFiles(Day day, MultipartFile... files) {
        List<Long> keep = trackService.getDetailBySlug(slugOf(day), ownerId).days().get(0).files().stream()
                .map(file -> file.fileId())
                .toList();
        trackEditService.edit(day.trackId(),
                new EditTrackRequest(null, TrackVisibility.PRIVATE,
                        List.of(new DayEdit(day.topicId(), "Day one", keep))),
                List.of(List.of(files)),
                ownerId);
    }

    /** Quizzes of this day. Tests commit, so other tests' quizzes are still there. */
    private long quizzesOf(Day day) {
        return quizRepository.findAll().stream()
                .filter(quiz -> quiz.getTopic().getTopicId().equals(day.topicId()))
                .count();
    }

    private String slugOf(Day day) {
        return trackService.getById(day.trackId(), ownerId).slug();
    }

    private static List<QuizQuestionResponse> fakeQuiz(int quiz) {
        return IntStream.rangeClosed(1, 10)
                .mapToObj(n -> new QuizQuestionResponse(
                        "Q" + n + " of quiz " + quiz,
                        List.of("right " + n, "wrong a", "wrong b", "wrong c"),
                        0,
                        "Because.",
                        "notes.pdf"))
                .toList();
    }

    private static MockMultipartFile pdf(String name) {
        return new MockMultipartFile("file", name, "application/pdf", ("%PDF " + name).getBytes());
    }

    private User user() {
        User user = new User();
        String unique = "quiz" + UUID.randomUUID().toString().substring(0, 8);
        user.setEmail(unique + "@example.com");
        user.setUsername(unique);
        return userRepository.save(user);
    }

    private static Path createStorage() {
        try {
            return Files.createTempDirectory("quiz-cache-test");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
