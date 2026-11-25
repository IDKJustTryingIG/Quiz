package com.abhi.quizbackend.email;

import com.abhi.quizbackend.entity.Enrollment;
import com.abhi.quizbackend.entity.QuizAttempt;
import com.abhi.quizbackend.entity.Track;
import com.abhi.quizbackend.properties.ContestProperties;
import com.abhi.quizbackend.properties.EmailProperties;
import com.abhi.quizbackend.properties.ScheduleProperties;
import com.abhi.quizbackend.repository.QuizAttemptRepository;
import com.abhi.quizbackend.repository.QuizSessionRepository;
import com.abhi.quizbackend.service.EnrollmentService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Writes the 06:00 email: today's day and topic, when the quiz opens, and
 * yesterday's rank if the user took part.
 *
 * Track and topic names come from users, so the HTML escapes them.
 */
@Component
@RequiredArgsConstructor
public class DailyEmailComposer {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a z", Locale.ENGLISH);

    private final QuizSessionRepository sessionRepository;
    private final QuizAttemptRepository attemptRepository;
    private final EmailProperties emailProperties;
    private final ContestProperties contestProperties;
    private final ScheduleProperties scheduleProperties;

    /** Call inside a transaction: it reads the track's days. */
    public OutgoingEmail compose(Long deliveryId, Enrollment enrollment, LocalDate date) {
        Track track = enrollment.getTrack();
        int dayNumber = EnrollmentService.dayNumber(enrollment.getStartDate(), date);
        int totalDays = track.getTrackTopics().size();
        String topic = track.getTrackTopics().get(dayNumber - 1).getTopic().getName();

        ZonedDateTime opens = date.atTime(contestProperties.getOpensAt()).atZone(scheduleProperties.getZone());
        String opensAt = TIME.format(opens);
        String startBy = TIME.format(opens.plus(contestProperties.getStartWindow()));
        Optional<String> yesterday = yesterdaysRank(enrollment, date.minusDays(1));

        String contestUrl = emailProperties.getAppUrl() + "/contest";
        String trackUrl = emailProperties.getAppUrl() + "/tracks/" + encode(track.getSlug());
        String token = encode(enrollment.getUnsubscribeToken());
        String unsubscribePage = emailProperties.getAppUrl() + "/unsubscribe?token=" + token;
        String oneClick = emailProperties.getApiUrl() + "/api/email/unsubscribe?token=" + token;

        String subject = "Day " + dayNumber + " of " + track.getName() + ": " + topic;

        String text = String.join("\n",
                "Today: " + topic + " (Day " + dayNumber + " of " + totalDays + ")",
                "",
                "Your quiz opens at " + opensAt + ". Start by " + startBy + ".",
                "Read today's files before then: " + trackUrl,
                yesterday.map(line -> "\n" + line).orElse(""),
                "",
                "The quiz: " + contestUrl,
                "",
                "You get this email because you enrolled in " + track.getName() + ".",
                "Stop daily emails: " + unsubscribePage);

        String html = """
                <!doctype html>
                <html><body style="margin:0;padding:24px;background:#f6f7f9;font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;color:#161a22">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:520px;margin:0 auto;background:#ffffff;border:1px solid #e3e6eb;border-radius:12px">
                    <tr><td style="padding:28px 28px 8px">
                      <p style="margin:0;font-size:13px;color:#5b6372">%s · Day %d of %d</p>
                      <h1 style="margin:6px 0 0;font-size:24px;line-height:1.25">Today: %s</h1>
                    </td></tr>
                    <tr><td style="padding:12px 28px">
                      <p style="margin:0 0 12px;font-size:15px;line-height:1.5">Your quiz opens at <b>%s</b>. Start by %s. Read today's files before then.</p>
                      %s
                      <p style="margin:20px 0 8px"><a href="%s" style="display:inline-block;background:#161a22;color:#ffffff;text-decoration:none;padding:10px 16px;border-radius:8px;font-size:14px">Open today's topic</a></p>
                    </td></tr>
                    <tr><td style="padding:16px 28px 24px;border-top:1px solid #e3e6eb">
                      <p style="margin:0;font-size:12px;line-height:1.5;color:#5b6372">You get this email because you enrolled in %s. <a href="%s" style="color:#5b6372">Stop daily emails</a>.</p>
                    </td></tr>
                  </table>
                </body></html>
                """.formatted(
                escape(track.getName()), dayNumber, totalDays, escape(topic),
                escape(opensAt), escape(startBy),
                yesterday.map(line -> "<p style=\"margin:0;font-size:14px;color:#5b6372\">" + escape(line) + "</p>").orElse(""),
                escape(trackUrl),
                escape(track.getName()), escape(unsubscribePage));

        return new OutgoingEmail(
                enrollment.getUser().getEmail(),
                subject,
                html,
                text,
                "daily-" + deliveryId,
                // RFC 8058: mail apps show their own Unsubscribe button, which POSTs to this URL.
                Map.of("List-Unsubscribe", "<" + oneClick + ">",
                        "List-Unsubscribe-Post", "List-Unsubscribe=One-Click"));
    }

    /** "Yesterday you ranked #4 of 23 with 8/10.", if the user took part. */
    private Optional<String> yesterdaysRank(Enrollment enrollment, LocalDate yesterday) {
        return sessionRepository.findByTrackTrackIdAndSessionDate(enrollment.getTrack().getTrackId(), yesterday)
                .flatMap(session -> {
                    List<QuizAttempt> scored = attemptRepository.findBySessionSessionId(session.getSessionId()).stream()
                            .filter(QuizAttempt::isScored)
                            .toList();
                    return scored.stream()
                            .filter(a -> a.getUser().getUserId().equals(enrollment.getUser().getUserId()))
                            .findFirst()
                            .map(mine -> {
                                long better = scored.stream()
                                        .filter(a -> a.getScore() > mine.getScore()
                                                || (a.getScore().equals(mine.getScore())
                                                    && a.getDurationMs() < mine.getDurationMs()))
                                        .count();
                                return "Yesterday you ranked #" + (better + 1) + " of " + scored.size()
                                        + " with " + mine.getScore() + "/" + mine.getTotal() + ".";
                            });
                });
    }

    private static String escape(String value) {
        return HtmlUtils.htmlEscape(value, "UTF-8");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
