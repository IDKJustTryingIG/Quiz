package com.abhi.quizbackend.email;

import com.abhi.quizbackend.entity.EmailDelivery;
import com.abhi.quizbackend.entity.EmailStatus;
import com.abhi.quizbackend.entity.Enrollment;
import com.abhi.quizbackend.entity.EnrollmentStatus;
import com.abhi.quizbackend.models.EmailDeliveryResponse;
import com.abhi.quizbackend.properties.ContestProperties;
import com.abhi.quizbackend.properties.EmailProperties;
import com.abhi.quizbackend.properties.ScheduleProperties;
import com.abhi.quizbackend.repository.EmailDeliveryRepository;
import com.abhi.quizbackend.repository.EnrollmentRepository;
import com.abhi.quizbackend.service.EnrollmentService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * The 06:00 email. First {@link #plan} writes one row per user who has a day
 * today. Then {@link #sendNext} sends the rows one at a time.
 *
 * The send runs outside any transaction, so a slow provider never holds a
 * database connection. A row is leased for a few minutes while it is sent,
 * so a second server does not send it too.
 */
@Service
public class DailyEmailService {

    private static final Logger log = LoggerFactory.getLogger(DailyEmailService.class);

    /** How long a row stays claimed while it is sent. */
    private static final Duration LEASE = Duration.ofMinutes(5);

    /** What one planning run did. */
    public record PlanResult(LocalDate date, int planned, int alreadyPlanned, int optedOut) {
    }

    private record Prepared(Long deliveryId, int attempts, OutgoingEmail email) {
    }

    private final EmailDeliveryRepository deliveryRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final DailyEmailComposer composer;
    private final EmailClient emailClient;
    private final EmailProperties emailProperties;
    private final ContestProperties contestProperties;
    private final ScheduleProperties scheduleProperties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public DailyEmailService(
            EmailDeliveryRepository deliveryRepository,
            EnrollmentRepository enrollmentRepository,
            DailyEmailComposer composer,
            EmailClient emailClient,
            EmailProperties emailProperties,
            ContestProperties contestProperties,
            ScheduleProperties scheduleProperties,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.deliveryRepository = deliveryRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.composer = composer;
        this.emailClient = emailClient;
        this.emailProperties = emailProperties;
        this.contestProperties = contestProperties;
        this.scheduleProperties = scheduleProperties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /**
     * Adds one email for each active, opted-in user who has a day of their
     * track on the date. Safe to repeat: a user never gets a second row.
     */
    public PlanResult plan(LocalDate date) {
        return transactionTemplate.execute(status -> {
            Set<Long> planned = deliveryRepository.findBySendDateAndKind(date, EmailDelivery.DAILY).stream()
                    .map(d -> d.getEnrollment().getEnrollmentId())
                    .collect(Collectors.toSet());
            Instant now = clock.instant();

            int added = 0;
            int already = 0;
            int optedOut = 0;
            for (Enrollment enrollment : enrollmentRepository.findActiveStartedBy(date)) {
                int day = EnrollmentService.dayNumber(enrollment.getStartDate(), date);
                if (day < 1 || day > enrollment.getTrack().getTrackTopics().size()) {
                    continue;
                }
                if (!enrollment.isEmailOptIn()) {
                    optedOut++;
                } else if (planned.contains(enrollment.getEnrollmentId())) {
                    already++;
                } else {
                    deliveryRepository.save(EmailDelivery.builder()
                            .enrollment(enrollment)
                            .sendDate(date)
                            .kind(EmailDelivery.DAILY)
                            .status(EmailStatus.PENDING)
                            .nextAttemptAt(now)
                            .build());
                    added++;
                }
            }
            return new PlanResult(date, added, already, optedOut);
        });
    }

    /** Sends every email that is due, with a pause between sends. */
    public int sendDue() {
        if (emailProperties.getResendApiKey().isBlank()) {
            return 0;
        }
        int sent = 0;
        while (sendNext()) {
            sent++;
            pause();
        }
        return sent;
    }

    /**
     * Sends the next due email.
     *
     * @return false if no email was due
     */
    public boolean sendNext() {
        Prepared prepared = transactionTemplate.execute(status -> claim());
        if (prepared == null) {
            return false;
        }
        if (prepared.email() == null) {
            // Skipped while claimed. Nothing to send.
            return true;
        }

        try {
            String providerId = emailClient.send(prepared.email());
            update(prepared, d -> {
                d.setStatus(EmailStatus.SENT);
                d.setProviderId(providerId);
                d.setLastError(null);
                d.setSentAt(clock.instant());
            });
        } catch (EmailSendException e) {
            boolean retry = e.isTemporary() && prepared.attempts() < emailProperties.getMaxAttempts();
            log.warn("Daily email {} failed, attempt {}: {}", prepared.deliveryId(), prepared.attempts(), e.getMessage());
            update(prepared, d -> {
                d.setLastError(e.getMessage());
                if (retry) {
                    d.setNextAttemptAt(clock.instant().plus(emailProperties.getRetryDelay()
                            .multipliedBy(1L << Math.min(prepared.attempts() - 1, 10))));
                } else {
                    d.setStatus(EmailStatus.FAILED);
                }
            });
        }
        return true;
    }

    /** The date's emails, for the admin page. */
    public List<EmailDeliveryResponse> deliveriesOn(LocalDate date) {
        return transactionTemplate.execute(status -> deliveryRepository.findBySendDateOrderByDeliveryIdAsc(date).stream()
                .map(d -> new EmailDeliveryResponse(
                        d.getDeliveryId(),
                        d.getSendDate(),
                        d.getEnrollment().getUser().getUsername(),
                        d.getEnrollment().getTrack().getName(),
                        d.getStatus(),
                        d.getAttempts(),
                        d.getNextAttemptAt(),
                        d.getLastError(),
                        d.getSentAt()))
                .toList());
    }

    /** Takes the next due row and writes its email, or skips it if it must not go out. */
    private Prepared claim() {
        Instant now = clock.instant();
        List<EmailDelivery> due = deliveryRepository.findDue(now, PageRequest.of(0, 1));
        if (due.isEmpty()) {
            return null;
        }
        EmailDelivery delivery = due.get(0);
        Enrollment enrollment = delivery.getEnrollment();

        String skip = null;
        if (enrollment.getStatus() != EnrollmentStatus.ACTIVE) {
            skip = "The user left the track.";
        } else if (!enrollment.isEmailOptIn()) {
            skip = "The user turned daily emails off.";
        } else if (!now.isBefore(contestOpensAt(delivery.getSendDate()))) {
            skip = "Too late: the quiz of the day already opened.";
        }
        if (skip != null) {
            delivery.setStatus(EmailStatus.SKIPPED);
            delivery.setLastError(skip);
            return new Prepared(delivery.getDeliveryId(), delivery.getAttempts(), null);
        }

        delivery.setAttempts(delivery.getAttempts() + 1);
        delivery.setNextAttemptAt(now.plus(LEASE));
        OutgoingEmail email = composer.compose(delivery.getDeliveryId(), enrollment, delivery.getSendDate());
        return new Prepared(delivery.getDeliveryId(), delivery.getAttempts(), email);
    }

    private Instant contestOpensAt(LocalDate date) {
        return date.atTime(contestProperties.getOpensAt()).atZone(scheduleProperties.getZone()).toInstant();
    }

    private void update(Prepared prepared, Consumer<EmailDelivery> change) {
        transactionTemplate.executeWithoutResult(status ->
                deliveryRepository.findById(prepared.deliveryId()).ifPresent(change));
    }

    private void pause() {
        try {
            Thread.sleep(emailProperties.getSendInterval().toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
