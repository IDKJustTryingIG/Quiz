import { apiRequest } from "@/lib/api";

/** The server's schedule zone. "Today" for the quiz queue is today here. */
export const SCHEDULE_ZONE = "Asia/Kolkata";

/** Today in the schedule zone, as "2026-10-13". */
export const scheduleToday = () =>
  new Intl.DateTimeFormat("en-CA", { timeZone: SCHEDULE_ZONE }).format(
    new Date(),
  );

/**
 * The quiz jobs of one date: [{ jobId, trackName, topicName, status,
 * attempts, nextAttemptAt, lastError, quizId, finishedAt, ... }].
 * status is PENDING, RUNNING, DONE or FAILED. Admins only.
 */
export const fetchQuizJobs = (date) =>
  apiRequest(`/api/admin/quiz-jobs?${new URLSearchParams({ date })}`);

/**
 * Plans the date now, as the 06:00 and 17:00 runs do. Safe to repeat.
 * Returns { date, needed, queued, alreadyReady, requeued }.
 */
export const planQuizzes = (date) =>
  apiRequest(`/api/admin/quiz-jobs/plan?${new URLSearchParams({ date })}`, {
    method: "POST",
  });

/**
 * The daily emails of one date: [{ deliveryId, username, trackName, status,
 * attempts, nextAttemptAt, lastError, sentAt }]. status is PENDING, SENT,
 * FAILED or SKIPPED. Admins only.
 */
export const fetchEmails = (date) =>
  apiRequest(`/api/admin/emails?${new URLSearchParams({ date })}`);

/**
 * Plans the date's emails now, as the 06:00 run does. Safe to repeat. The
 * server sends them within 30 seconds. Returns { planned, alreadyPlanned, optedOut }.
 */
export const planEmails = (date) =>
  apiRequest(`/api/admin/emails/plan?${new URLSearchParams({ date })}`, {
    method: "POST",
  });
