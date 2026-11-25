package com.abhi.quizbackend.service;

import com.abhi.quizbackend.entity.Enrollment;
import com.abhi.quizbackend.entity.EnrollmentStatus;
import com.abhi.quizbackend.entity.TrackVisibility;
import com.abhi.quizbackend.entity.User;
import com.abhi.quizbackend.exception.EnrollmentConflictException;
import com.abhi.quizbackend.exception.ResourceNotFoundException;
import com.abhi.quizbackend.models.CreateFullTrackRequest;
import com.abhi.quizbackend.models.CreateFullTrackRequest.TopicDraft;
import com.abhi.quizbackend.models.EnrollmentResponse;
import com.abhi.quizbackend.models.EnrollmentResponse.Progress;
import com.abhi.quizbackend.properties.FileStorageProperties;
import com.abhi.quizbackend.properties.ScheduleProperties;
import com.abhi.quizbackend.repository.EnrollmentRepository;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs enrollment against a real JPA stack on H2. The clock is a test clock,
 * so a test can move to another day.
 *
 * H2 cannot create the partial index that allows one active enrollment per
 * user, so the race between two enroll requests is not tested here.
 */
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@Import({EnrollmentService.class, TrackService.class, TrackCreationService.class,
        OrphanCleaner.class, TrackFileStore.class, LocalFileStorageService.class,
        EnrollmentIntegrationTest.Clocks.class})
@EnableConfigurationProperties({FileStorageProperties.class, ScheduleProperties.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EnrollmentIntegrationTest {

    /** 13 Oct 2026, 10:00 in India. */
    private static final Instant MORNING = Instant.parse("2026-10-13T04:30:00Z");

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("app.file-storage.location", EnrollmentIntegrationTest::createStorage);
    }

    @TestConfiguration
    static class Clocks {
        @Bean
        TestClock clock() {
            return new TestClock(MORNING);
        }
    }

    @Autowired EnrollmentService enrollmentService;
    @Autowired TrackCreationService trackCreationService;
    @Autowired EnrollmentRepository enrollmentRepository;
    @Autowired UserRepository userRepository;
    @Autowired TestClock clock;

    private Long userId;

    @BeforeEach
    void setUp() {
        clock.set(MORNING);
        userId = user().getUserId();
    }

    @Test
    void dayOneIsTheDayAfterEnrolling() {
        Long trackId = track(3, TrackVisibility.PUBLIC, userId);

        EnrollmentResponse enrolled = enrollmentService.enroll(trackId, userId, false);

        assertThat(enrolled.startDate()).isEqualTo(LocalDate.of(2026, 10, 14));
        assertThat(enrolled.dayNumber()).isZero();
        assertThat(enrolled.progress()).isEqualTo(Progress.NOT_STARTED);
        assertThat(enrolled.today()).isNull();
    }

    @Test
    void theDayMovesOnWithTheCalendarAndEndsAfterTheLastDay() {
        Long trackId = track(2, TrackVisibility.PUBLIC, userId);
        enrollmentService.enroll(trackId, userId, false);

        clock.plusDays(1);
        EnrollmentResponse day1 = enrollmentService.current(userId).orElseThrow();
        clock.plusDays(1);
        EnrollmentResponse day2 = enrollmentService.current(userId).orElseThrow();
        clock.plusDays(1);
        EnrollmentResponse after = enrollmentService.current(userId).orElseThrow();

        assertThat(day1.dayNumber()).isEqualTo(1);
        assertThat(day1.today().topicName()).isEqualTo("Topic 1");
        assertThat(day2.today().topicName()).isEqualTo("Topic 2");
        assertThat(after.progress()).isEqualTo(Progress.FINISHED);
        assertThat(after.today()).isNull();
    }

    @Test
    void todayUsesTheScheduleTimeZoneNotUtc() {
        Long trackId = track(1, TrackVisibility.PUBLIC, userId);
        // 19:00 UTC on 13 Oct is 00:30 on 14 Oct in India.
        clock.set(Instant.parse("2026-10-13T19:00:00Z"));

        EnrollmentResponse enrolled = enrollmentService.enroll(trackId, userId, false);

        assertThat(enrolled.startDate()).isEqualTo(LocalDate.of(2026, 10, 15));
    }

    @Test
    void aUserFollowsOneTrackAtATime() {
        Long first = track(3, TrackVisibility.PUBLIC, userId);
        Long second = track(3, TrackVisibility.PUBLIC, userId);
        enrollmentService.enroll(first, userId, false);

        assertThatThrownBy(() -> enrollmentService.enroll(second, userId, false))
                .isInstanceOf(EnrollmentConflictException.class);
        assertThatThrownBy(() -> enrollmentService.enroll(first, userId, false))
                .isInstanceOf(EnrollmentConflictException.class);
    }

    @Test
    void switchingLeavesTheOldTrack() {
        Long first = track(3, TrackVisibility.PUBLIC, userId);
        Long second = track(3, TrackVisibility.PUBLIC, userId);
        enrollmentService.enroll(first, userId, false);

        EnrollmentResponse switched = enrollmentService.enroll(second, userId, true);

        assertThat(switched.trackId()).isEqualTo(second);
        assertThat(statusesOf(userId)).containsExactlyInAnyOrder(EnrollmentStatus.LEFT, EnrollmentStatus.ACTIVE);
    }

    @Test
    void aFinishedTrackDoesNotBlockTheNextOne() {
        Long first = track(1, TrackVisibility.PUBLIC, userId);
        Long second = track(3, TrackVisibility.PUBLIC, userId);
        enrollmentService.enroll(first, userId, false);
        clock.plusDays(2);

        enrollmentService.enroll(second, userId, false);

        assertThat(statusesOf(userId)).containsExactlyInAnyOrder(EnrollmentStatus.COMPLETED, EnrollmentStatus.ACTIVE);
    }

    @Test
    void leavingEndsTheEnrollment() {
        Long trackId = track(3, TrackVisibility.PUBLIC, userId);
        enrollmentService.enroll(trackId, userId, false);

        enrollmentService.leave(trackId, userId);

        assertThat(enrollmentService.current(userId)).isEmpty();
        assertThatThrownBy(() -> enrollmentService.leave(trackId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aPrivateTrackOfAnotherUserCannotBeFound() {
        Long ownerId = user().getUserId();
        Long trackId = track(3, TrackVisibility.PRIVATE, ownerId);

        assertThatThrownBy(() -> enrollmentService.enroll(trackId, userId, false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- helpers ----------

    private List<EnrollmentStatus> statusesOf(Long userId) {
        return enrollmentRepository.findAll().stream()
                .filter(e -> e.getUser().getUserId().equals(userId))
                .map(Enrollment::getStatus)
                .toList();
    }

    private Long track(int days, TrackVisibility visibility, Long ownerId) {
        List<TopicDraft> topics = IntStream.rangeClosed(1, days)
                .mapToObj(n -> new TopicDraft("Topic " + n))
                .toList();
        return trackCreationService.create(
                new CreateFullTrackRequest("Track " + UUID.randomUUID(), null, visibility, topics),
                topics.stream().map(topic -> List.<org.springframework.web.multipart.MultipartFile>of()).toList(),
                ownerId).trackId();
    }

    private User user() {
        User user = new User();
        String unique = "enroll" + UUID.randomUUID().toString().substring(0, 8);
        user.setEmail(unique + "@example.com");
        user.setUsername(unique);
        return userRepository.save(user);
    }

    private static String createStorage() {
        try {
            return Files.createTempDirectory("enrollment-test").toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
