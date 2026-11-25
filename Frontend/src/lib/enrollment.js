import { useEffect } from "react";
import { create } from "zustand";
import { apiRequest } from "@/lib/api";
import { useAuth } from "@/lib/auth";

/**
 * The track the user follows, with today's day:
 * { enrollmentId, trackId, trackName, trackSlug, startDate, dayNumber,
 *   totalDays, progress, today }, or null if they follow none.
 *
 * progress is NOT_STARTED (Day 1 is a later date), IN_PROGRESS or FINISHED.
 * today is { topicId, topicName } while IN_PROGRESS, else null.
 * emailOptIn says whether the user gets the 06:00 email.
 */
const fetchCurrentEnrollment = () => apiRequest("/api/enrollments/current");

/**
 * The user's enrollment, held in one place so the track page and the Today
 * card agree after the user enrolls, switches or leaves.
 */
export const useEnrollment = create((set, get) => ({
  /** "idle" until loaded for the signed-in user, then "loading", "ready" or "error". */
  status: "idle",
  /** Whose enrollment this is, so another user never sees it after a new sign-in. */
  userId: null,
  enrollment: null,
  error: null,

  load: async (userId) => {
    const { status, userId: loadedFor } = get();
    if (loadedFor === userId && (status === "loading" || status === "ready"))
      return;
    set({ status: "loading", userId, enrollment: null, error: null });
    try {
      const enrollment = await fetchCurrentEnrollment();
      if (get().userId === userId) set({ status: "ready", enrollment });
    } catch (cause) {
      if (get().userId === userId)
        set({ status: "error", error: cause.message });
    }
  },

  /**
   * Day 1 is tomorrow. If the user follows another track, the server refuses
   * with a 409 unless switchTrack is true; then the user leaves that track.
   */
  enroll: async (trackId, { switchTrack = false } = {}) => {
    const query = switchTrack ? "?switch=true" : "";
    const enrollment = await apiRequest(
      `/api/tracks/${trackId}/enrollment${query}`,
      { method: "POST" },
    );
    set({ status: "ready", enrollment, error: null });
    return enrollment;
  },

  /** Turns the 06:00 email on or off. */
  setEmail: async (trackId, enabled) => {
    const enrollment = await apiRequest(
      `/api/tracks/${trackId}/enrollment/email`,
      { method: "PUT", body: { enabled } },
    );
    set({ status: "ready", enrollment, error: null });
  },

  leave: async (trackId) => {
    await apiRequest(`/api/tracks/${trackId}/enrollment`, { method: "DELETE" });
    set({ status: "ready", enrollment: null, error: null });
  },
}));

/** Loads the signed-in user's enrollment once, and returns the store's state. */
export const useCurrentEnrollment = () => {
  const userId = useAuth((s) => s.user?.userId ?? null);
  const load = useEnrollment((s) => s.load);
  const state = useEnrollment();

  useEffect(() => {
    if (userId !== null) load(userId);
  }, [userId, load]);

  if (userId === null || state.userId !== userId)
    return { ...state, status: "idle", enrollment: null };
  return state;
};

/** "2026-10-14" → "Wed, 14 Oct", read as a calendar date with no time zone. */
export const formatDay = (isoDate) => {
  const [year, month, day] = isoDate.split("-").map(Number);
  return new Date(year, month - 1, day).toLocaleDateString(undefined, {
    weekday: "short",
    day: "numeric",
    month: "short",
  });
};
