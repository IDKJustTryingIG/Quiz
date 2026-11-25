package com.abhi.quizbackend.email;

import com.abhi.quizbackend.entity.AttemptStatus;
import com.abhi.quizbackend.entity.EmailDelivery;
import com.abhi.quizbackend.entity.EmailStatus;
import com.abhi.quizbackend.entity.Enrollment;
import com.abhi.quizbackend.entity.EnrollmentStatus;
import com.abhi.quizbackend.entity.QuizAttempt;
import com.abhi.quizbackend.entity.QuizSession;
import com.abhi.quizbackend.entity.TrackVisibility;
import com.abhi.quizbackend.entity.User;
import com.abhi.quizbackend.models.CreateFullTrackRequest;
import com.abhi.quizbackend.models.CreateFullTrackRequest.TopicDraft;
import com.abhi.quizbackend.properties.ContestProperties;
import com.abhi.quizbackend.properties.EmailProperties;
import com.abhi.quizbackend.properties.FileStorageProperties;
import com.abhi.quizbackend.properties.ScheduleProperties;
import com.abhi.quizbackend.repository.EmailDeliveryRepository;
import com.abhi.quizbackend.repository.EnrollmentRepository;
import com.abhi.quizbackend.repository.QuizAttemptRepository;
import com.abhi.quizbackend.repository.QuizSessionRepository;
import com.abhi.quizbackend.repository.TrackRepository;
import com.abhi.quizbackend.repository.UserRepository;
import com.abhi.quizbackend.service.EnrollmentService;
import com.abhi.quizbackend.service.LocalFileStorageService;
import com.abhi.quizbackend.service.OrphanCleaner;
import com.abhi.quizbackend.service.TrackCreationService;
import com.abhi.quizbackend.service.TrackFileStore;
import com.abhi.quizbackend.service.TrackService;
import com.abhi.quizbackend.support.TestClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plans and sends the 06:00 email against a real JPA stack on H2. A fake
 * client stands in for Resend: it records each email, and fails when a test
 * asks it to.
 */
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "app.email.resend-api-key=test-key",
        "app.email.send-interval=PT0S"
})
@Import({DailyEmailService.class, DailyEmailComposer.class, EnrollmentService.class, TrackService.class,
        TrackCreationService.class, OrphanCleaner.class, TrackFileStore.class, LocalFileStorageService.class,
        DailyEmailIntegrationTest.Beans.class})
@EnableConfigurationProperties({FileStorageProperties.class, ScheduleProperties.class, ContestProperties.class,
        EmailProperties.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DailyEmailIntegrationTest {

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final Instant MORNING = Instant.parse("2026-10-13T00:30:00Z");

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("app.file-storage.location", DailyEmailIntegrationTest::createStorage);
    }

    /** Records each email. Throws the next queued failure first, if there is one. */
    static class FakeEmailClient implements EmailClient {
        final List<OutgoingEmail> sent = new ArrayList<>();
        final List<EmailSendException> failures = new ArrayList<>();

        @Override
        public String send(OutgoingEmail email) {
            if (!failures.isEmpty()) {
                throw failures.remove(0);
            }
            sent.add(email);
            return "re_" + sent.size();
        }
    }

    @TestConfiguration
    static class Beans {
        @Bean
        TestClock clock() {
            return new TestClock(MORNING);
        }

        @Bean
        FakeEmailClient emailClient() {
            return new FakeEmailClient();
        }
    }

    @Autowired DailyEmailService emailService;
    @Autowired EnrollmentService enrollmentService;
    @Autowired TrackCreationService trackCreationService;
    @Autowired EmailDeliveryRepository deliveryRepository;
    @Autowired EnrollmentRepository enrollmentRepository;
    @Autowired QuizSessionRepository sessionRepository;
    @Autowired QuizAttemptRepository attemptRepository;
    @Autowired TrackRepository trackRepository;
    @Autowired UserRepository userRepository;
    @Autowired FakeEmailClient client;
    @Autowired TestClock clock;

    private Long ownerId;

    @BeforeEach
    void setUp() {
        // Each test is on its own date, at 06:00 in India.
        clock.plusDays(10);
        clock.set(at(LocalTime.of(6, 0)));
        deliveryRepository.deleteAll();
        client.sent.clear();
        client.failures.clear();
        ownerId = user("owner").getUserId();
    }

    @Test
    void plansOneEmailForEachUserWithADayToday() {
        Long trackId = track("Graphs");
        Long onDay = enrolledYesterday(trackId);
        Long turnedOff = enrolledYesterday(trackId);
        enrollmentService.setEmailOptIn(trackId, turnedOff, false);
        // Enrolls today, so Day 1 is tomorrow: no email yet.
        enrollmentService.enroll(trackId, user("new").getUserId(), false);

        DailyEmailService.PlanResult first = emailService.plan(today());
        DailyEmailService.PlanResult again = emailService.plan(today());

        assertThat(first.planned()).isEqualTo(1);
        assertThat(first.optedOut()).isEqualTo(1);
        assertThat(again.planned()).isZero();
        assertThat(again.alreadyPlanned()).isEqualTo(1);
        assertThat(deliveriesToday()).singleElement()
                .satisfies(d -> assertThat(d.getEnrollment().getUser().getUserId()).isEqualTo(onDay));
    }

    @Test
    void theEmailNamesTheDayTheTopicAndTheQuizTime() {
        Long trackId = track("<b>Graphs</b>");
        Long userId = enrolledYesterday(trackId);
        emailService.plan(today());

        emailService.sendDue();

        OutgoingEmail email = client.sent.get(0);
        Enrollment enrollment = activeEnrollment(userId);
        EmailDelivery delivery = deliveriesToday().get(0);
        assertThat(email.to()).isEqualTo(userRepository.findById(userId).orElseThrow().getEmail());
        assertThat(email.subject()).startsWith("Day 1 of ").endsWith(": <b>Graphs</b>");
        // A user's topic name is text, never HTML.
        assertThat(email.html()).contains("&lt;b&gt;Graphs&lt;/b&gt;").doesNotContain("<b>Graphs</b>");
        assertThat(email.text()).contains("Your quiz opens at 6:00 PM IST. Start by 6:05 PM IST.");
        assertThat(email.html()).contains("/unsubscribe?token=" + enrollment.getUnsubscribeToken());
        assertThat(email.headers().get("List-Unsubscribe")).contains("/api/email/unsubscribe?token=");
        assertThat(email.idempotencyKey()).isEqualTo("daily-" + delivery.getDeliveryId());
        assertThat(delivery.getStatus()).isEqualTo(EmailStatus.SENT);
        assertThat(delivery.getProviderId()).isEqualTo("re_1");
    }

    @Test
    void theEmailGivesYesterdaysRank() {
        Long trackId = track("Graphs", "Trees");
        Long userId = enrolledYesterday(trackId);
        clock.plusDays(1);
        // Yesterday's contest: one user scored more, so this user was second of two.
        QuizSession yesterday = sessionRepository.save(QuizSession.builder()
                .track(trackRepository.getReferenceById(trackId))
                .sessionDate(today().minusDays(1))
                .opensAt(clock.instant().minus(Duration.ofHours(12)))
                .startDeadline(clock.instant().minus(Duration.ofHours(12)))
                .closesAt(clock.instant().minus(Duration.ofHours(11)))
                .build());
        attempt(yesterday, user("rival"), 9);
        attempt(yesterday, userRepository.findById(userId).orElseThrow(), 7);
        emailService.plan(today());

        emailService.sendDue();

        assertThat(client.sent.get(0).subject()).startsWith("Day 2 of ").endsWith(": Trees");
        assertThat(client.sent.get(0).text()).contains("Yesterday you ranked #2 of 2 with 7/10.");
    }

    @Test
    void aTemporaryFailureIsTriedAgainLater() {
        enrolledYesterday(track("Graphs"));
        emailService.plan(today());
        client.failures.add(new EmailSendException("Resend 429: rate limit", true, null));

        emailService.sendNext();
        assertThat(deliveriesToday().get(0).getStatus()).isEqualTo(EmailStatus.PENDING);
        assertThat(emailService.sendNext()).isFalse();

        clock.plus(Duration.ofMinutes(1));
        emailService.sendNext();

        assertThat(deliveriesToday().get(0).getStatus()).isEqualTo(EmailStatus.SENT);
        assertThat(deliveriesToday().get(0).getAttempts()).isEqualTo(2);
    }

    @Test
    void aRefusedEmailFailsAtOnce() {
        enrolledYesterday(track("Graphs"));
        emailService.plan(today());
        client.failures.add(new EmailSendException(
                "Resend 403: You can only send testing emails to your own email address", false, null));

        emailService.sendNext();

        EmailDelivery failed = deliveriesToday().get(0);
        assertThat(failed.getStatus()).isEqualTo(EmailStatus.FAILED);
        assertThat(failed.getLastError()).contains("your own email address");
    }

    @Test
    void anEmailIsSkippedOnceTheQuizHasOpened() {
        enrolledYesterday(track("Graphs"));
        emailService.plan(today());
        clock.set(at(LocalTime.of(18, 1)));

        emailService.sendDue();

        assertThat(client.sent).isEmpty();
        assertThat(deliveriesToday().get(0).getStatus()).isEqualTo(EmailStatus.SKIPPED);
    }

    @Test
    void theUnsubscribeLinkTurnsTheEmailsOff() {
        Long trackId = track("Graphs", "Trees");
        Long userId = enrolledYesterday(trackId);
        emailService.plan(today());
        String token = activeEnrollment(userId).getUnsubscribeToken();

        assertThat(enrollmentService.unsubscribe(token)).isTrue();
        assertThat(enrollmentService.unsubscribe("not-a-token")).isFalse();
        emailService.sendDue();

        assertThat(client.sent).isEmpty();
        assertThat(deliveriesToday().get(0).getStatus()).isEqualTo(EmailStatus.SKIPPED);
        assertThat(emailService.plan(today().plusDays(1)).optedOut()).isEqualTo(1);
    }

    // ---------- helpers ----------

    private LocalDate today() {
        return enrollmentService.today();
    }

    private Instant at(LocalTime time) {
        return enrollmentService.today().atTime(time).atZone(INDIA).toInstant();
    }

    private List<EmailDelivery> deliveriesToday() {
        return deliveryRepository.findBySendDateOrderByDeliveryIdAsc(today());
    }

    private Enrollment activeEnrollment(Long userId) {
        return enrollmentRepository.findByUserUserIdAndStatus(userId, EnrollmentStatus.ACTIVE).orElseThrow();
    }

    /** A new user who enrolled yesterday, so today is Day 1. */
    private Long enrolledYesterday(Long trackId) {
        Long userId = user("u").getUserId();
        Instant now = clock.instant();
        clock.plusDays(-1);
        enrollmentService.enroll(trackId, userId, false);
        clock.set(now);
        return userId;
    }

    private void attempt(QuizSession session, User user, int score) {
        attemptRepository.save(QuizAttempt.builder()
                .session(session)
                .user(user)
                .dayNumber(1)
                .status(AttemptStatus.SUBMITTED)
                .startedAt(session.getOpensAt())
                .deadlineAt(session.getClosesAt())
                .submittedAt(session.getOpensAt().plusSeconds(200))
                .score(score)
                .total(10)
                .durationMs(200_000L)
                .build());
    }

    private Long track(String... days) {
        List<TopicDraft> topics = Arrays.stream(days).map(TopicDraft::new).toList();
        List<List<MultipartFile>> files = topics.stream().map(t -> List.<MultipartFile>of()).toList();
        return trackCreationService.create(
                new CreateFullTrackRequest("Track " + UUID.randomUUID(), null, TrackVisibility.PUBLIC, topics),
                files, ownerId).trackId();
    }

    private User user(String name) {
        User user = new User();
        String unique = name + UUID.randomUUID().toString().substring(0, 8);
        user.setEmail(unique + "@example.com");
        user.setUsername(unique);
        return userRepository.save(user);
    }

    private static String createStorage() {
        try {
            return Files.createTempDirectory("email-test").toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
