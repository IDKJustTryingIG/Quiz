import { useEffect, useState } from "react";
import { apiRequest } from "@/lib/api";

/**
 * Today's contest of the user's track:
 * { state, serverTime, sessionId, trackName, trackSlug, dayNumber, totalDays,
 *   topicName, opensAt, startDeadline, closesAt, quizReady, canStart, attempt }.
 *
 * state is NONE, NOT_PLANNED, UPCOMING, OPEN or CLOSED. attempt is
 * { attemptId, status, deadlineAt, score, total, durationMs } or null.
 */
export const fetchContestToday = () => apiRequest("/api/contests/today");

/** Starts the attempt, or returns the one already started. The time limit starts now. */
export const startAttempt = (sessionId) =>
  apiRequest(`/api/contests/${sessionId}/attempt`, { method: "POST" });

/**
 * An attempt: { attemptId, status, deadlineAt, serverTime,
 * questions: [{ questionId, question, options }], answers: [{ questionId, chosenIndex }] }.
 */
export const fetchAttempt = (attemptId) =>
  apiRequest(`/api/attempts/${attemptId}`);

/** Saves one answer. The first answer to a question is final. */
export const saveAnswer = (attemptId, questionId, chosenIndex) =>
  apiRequest(`/api/attempts/${attemptId}/answers`, {
    method: "PUT",
    body: { questionId, chosenIndex },
  });

/**
 * Ends the attempt: { status, score, total, durationMs, revealed, revealAt, results }.
 * results stays empty until the contest closes.
 */
export const submitAttempt = (attemptId) =>
  apiRequest(`/api/attempts/${attemptId}/submit`, { method: "POST" });

export const fetchResult = (attemptId) =>
  apiRequest(`/api/attempts/${attemptId}/result`);

/**
 * { isFinal, closesAt, serverTime, entries: [{ rank, username, dayNumber,
 * status, score, total, durationMs, you }] }. rank is null while answering.
 */
export const fetchLeaderboard = (sessionId) =>
  apiRequest(`/api/contests/${sessionId}/leaderboard`);

/**
 * The server's time, ticking every second. The countdown follows the server
 * clock, so a browser clock that is a few minutes wrong cannot open the
 * contest early or late on screen.
 *
 * serverTime is an ISO time the server sent with a response.
 */
export const useServerNow = (serverTime) => {
  const [offset] = useState(() =>
    serverTime ? Date.parse(serverTime) - Date.now() : 0,
  );
  const [now, setNow] = useState(() => Date.now() + offset);
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now() + offset), 1000);
    return () => clearInterval(id);
  }, [offset]);
  return now;
};

/** 125 seconds → "2:05". 3725 seconds → "1:02:05". */
export const formatSeconds = (totalSeconds) => {
  const s = Math.max(0, Math.ceil(totalSeconds));
  const hours = Math.floor(s / 3600);
  const minutes = Math.floor((s % 3600) / 60);
  const seconds = String(s % 60).padStart(2, "0");
  return hours > 0
    ? `${hours}:${String(minutes).padStart(2, "0")}:${seconds}`
    : `${minutes}:${seconds}`;
};

/** An ISO time as "6:00 PM", in the browser's zone. */
export const formatClock = (iso) =>
  new Date(iso).toLocaleTimeString(undefined, {
    hour: "numeric",
    minute: "2-digit",
  });

/** 252000 → "4:12". */
export const formatDuration = (ms) =>
  ms == null ? "" : formatSeconds(ms / 1000);
