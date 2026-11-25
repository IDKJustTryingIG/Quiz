package com.abhi.quizbackend.service;

import com.abhi.quizbackend.entity.Enrollment;
import com.abhi.quizbackend.entity.EnrollmentStatus;
import com.abhi.quizbackend.entity.Track;
import com.abhi.quizbackend.entity.TrackTopic;
import com.abhi.quizbackend.exception.EnrollmentConflictException;
import com.abhi.quizbackend.exception.ResourceNotFoundException;
import com.abhi.quizbackend.models.EnrollmentResponse;
import com.abhi.quizbackend.models.EnrollmentResponse.Progress;
import com.abhi.quizbackend.properties.ScheduleProperties;
import com.abhi.quizbackend.repository.EnrollmentRepository;
import com.abhi.quizbackend.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Enrolls users in tracks. A user follows one track at a time.
 *
 * Day 1 is the day after the user enrolls, so every user gets a full first
 * day: the morning email and a quiz that the daily job prepared. After that
 * the day number moves on with the calendar, in {@link ScheduleProperties#getZone()}.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class EnrollmentService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final EnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;
    private final TrackService trackService;
    private final ScheduleProperties scheduleProperties;
    private final Clock clock;

    /**
     * Enrolls the user in a track they can see.
     *
     * If the user already follows another track, the request fails unless
     * switchTrack is true; then the old enrollment ends as LEFT. A finished
     * track never blocks: it ends as COMPLETED.
     */
    public EnrollmentResponse enroll(Long trackId, Long userId, boolean switchTrack) {
        Track track = trackService.findReadableTrack(trackId, userId);
        if (track.getTrackTopics().isEmpty()) {
            throw new EnrollmentConflictException("This track has no days yet");
        }

        LocalDate today = today();
        Optional<Enrollment> current = enrollmentRepository.findByUserUserIdAndStatus(userId, EnrollmentStatus.ACTIVE);
        if (current.isPresent()) {
            Enrollment existing = current.get();
            if (isFinished(existing, today)) {
                end(existing, EnrollmentStatus.COMPLETED);
            } else if (existing.getTrack().getTrackId().equals(trackId)) {
                throw new EnrollmentConflictException("You are already enrolled in this track");
            } else if (switchTrack) {
                end(existing, EnrollmentStatus.LEFT);
            } else {
                throw new EnrollmentConflictException(
                        "You are already enrolled in \"" + existing.getTrack().getName() + "\"");
            }
            // The insert below runs at once (IDENTITY ids), before Hibernate
            // would flush this update. Flush first, or the one-active index
            // still sees the old row as ACTIVE.
            enrollmentRepository.flush();
        }

        Enrollment enrollment = Enrollment.builder()
                .user(userRepository.getReferenceById(userId))
                .track(track)
                .startDate(today.plusDays(1))
                .status(EnrollmentStatus.ACTIVE)
                .unsubscribeToken(newToken())
                .build();
        try {
            enrollmentRepository.saveAndFlush(enrollment);
        } catch (DataIntegrityViolationException e) {
            // Another request of this user enrolled them first.
            throw new EnrollmentConflictException("You enrolled in another track at the same time. Reload the page.");
        }

        return toResponse(enrollment, today);
    }

    /** Turns the 06:00 email on or off for the user's enrollment in this track. */
    public EnrollmentResponse setEmailOptIn(Long trackId, Long userId, boolean enabled) {
        Enrollment enrollment = activeIn(trackId, userId);
        enrollment.setEmailOptIn(enabled);
        return toResponse(enrollment, today());
    }

    /**
     * Turns the 06:00 email off for the enrollment with this token. The
     * "Stop daily emails" link calls this, without a sign-in.
     *
     * @return false if no enrollment has the token
     */
    public boolean unsubscribe(String token) {
        return enrollmentRepository.findByUnsubscribeToken(token)
                .map(enrollment -> {
                    enrollment.setEmailOptIn(false);
                    return true;
                })
                .orElse(false);
    }

    /** Ends the user's enrollment in this track. */
    public void leave(Long trackId, Long userId) {
        end(activeIn(trackId, userId), EnrollmentStatus.LEFT);
    }

    private Enrollment activeIn(Long trackId, Long userId) {
        return enrollmentRepository.findByUserUserIdAndStatus(userId, EnrollmentStatus.ACTIVE)
                .filter(e -> e.getTrack().getTrackId().equals(trackId))
                .orElseThrow(() -> new ResourceNotFoundException("You are not enrolled in this track"));
    }

    /** 32 random bytes as hex: too many to guess. */
    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /** The user's active enrollment and today's topic, if they follow a track. */
    @Transactional(readOnly = true)
    public Optional<EnrollmentResponse> current(Long userId) {
        LocalDate today = today();
        return enrollmentRepository.findByUserUserIdAndStatus(userId, EnrollmentStatus.ACTIVE)
                .map(enrollment -> toResponse(enrollment, today));
    }

    /**
     * The day of the track on a date: 1 on the start date, then one more each
     * day. 0 before the start date.
     */
    public static int dayNumber(LocalDate startDate, LocalDate date) {
        long days = ChronoUnit.DAYS.between(startDate, date) + 1;
        return (int) Math.max(0, days);
    }

    /** Today in the schedule's time zone, not the server's. */
    public LocalDate today() {
        return LocalDate.now(clock.withZone(scheduleProperties.getZone()));
    }

    private boolean isFinished(Enrollment enrollment, LocalDate today) {
        return dayNumber(enrollment.getStartDate(), today) > enrollment.getTrack().getTrackTopics().size();
    }

    private void end(Enrollment enrollment, EnrollmentStatus status) {
        enrollment.setStatus(status);
        enrollment.setEndedAt(LocalDateTime.now(clock.withZone(scheduleProperties.getZone())));
    }

    private EnrollmentResponse toResponse(Enrollment enrollment, LocalDate today) {
        Track track = enrollment.getTrack();
        List<TrackTopic> days = track.getTrackTopics();
        int dayNumber = dayNumber(enrollment.getStartDate(), today);

        Progress progress;
        EnrollmentResponse.Today topic = null;
        if (dayNumber < 1) {
            progress = Progress.NOT_STARTED;
        } else if (dayNumber > days.size()) {
            progress = Progress.FINISHED;
        } else {
            progress = Progress.IN_PROGRESS;
            // Days are ordered by position, so day N is the Nth entry.
            TrackTopic day = days.get(dayNumber - 1);
            topic = new EnrollmentResponse.Today(day.getTopic().getTopicId(), day.getTopic().getName());
        }

        return new EnrollmentResponse(
                enrollment.getEnrollmentId(),
                track.getTrackId(),
                track.getName(),
                track.getSlug(),
                enrollment.getStartDate(),
                dayNumber,
                days.size(),
                progress,
                topic,
                enrollment.isEmailOptIn()
        );
    }
}
