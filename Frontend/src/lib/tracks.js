import { apiBlob, apiRequest } from "@/lib/api";

/** Kept in step with the server's whitelist in TrackCreationService. */
export const RESOURCE_ACCEPT =
  ".pdf,.doc,.docx,.ppt,.pptx,.xls,.xlsx,.txt,.md,.png,.jpg,.jpeg,.gif,.webp";
export const MAX_RESOURCE_BYTES = 10 * 1024 * 1024;

/** scope is "public" (every public track) or "mine" (the user's own). */
export const fetchTracks = (scope, query = "") => {
  const params = new URLSearchParams({ scope });
  if (query.trim()) params.set("q", query.trim());
  return apiRequest(`/api/tracks?${params}`);
};

/**
 * Sends the whole track in one multipart request, so the server can save it
 * all or nothing. Topic i is day i + 1; its files go in the "files-{i}" parts.
 */
export const createTrack = ({ name, description, visibility, topics }) => {
  const form = new FormData();
  const track = {
    name,
    description,
    visibility,
    topics: topics.map((topic) => ({ name: topic.name })),
  };
  // Typed as JSON so Spring reads this part with its JSON converter.
  form.append(
    "track",
    new Blob([JSON.stringify(track)], { type: "application/json" }),
  );
  topics.forEach((topic, i) => {
    topic.files.forEach((file) => form.append(`files-${i}`, file));
  });
  return apiRequest("/api/tracks/full", { method: "POST", body: form });
};

/**
 * Saves the owner's edit of a track in one multipart request: all of it or
 * none of it. The name cannot change, so it is not sent.
 *
 * Each day is { topicId (null for a new day), name, keptFiles, files }.
 * Existing files not in keptFiles are removed. Day i's new files go in the
 * "files-{i}" parts.
 */
export const updateTrack = (trackId, { description, visibility, days }) => {
  const form = new FormData();
  const track = {
    description,
    visibility,
    days: days.map((day) => ({
      topicId: day.topicId ?? null,
      name: day.name,
      keepFileIds: day.keptFiles.map((file) => file.fileId),
    })),
  };
  form.append(
    "track",
    new Blob([JSON.stringify(track)], { type: "application/json" }),
  );
  days.forEach((day, i) => {
    day.files.forEach((file) => form.append(`files-${i}`, file));
  });
  return apiRequest(`/api/tracks/${trackId}`, { method: "PUT", body: form });
};

/** Deletes a track the user owns, with its days and every file only it uses. */
export const deleteTrack = (trackId) =>
  apiRequest(`/api/tracks/${trackId}`, { method: "DELETE" });

/** Track names are unique across all users, compared exactly. */
export const checkTrackName = (name) =>
  apiRequest(
    `/api/tracks/name-availability?${new URLSearchParams({ name: name.trim() })}`,
  ).then((body) => body.available);

/** One page of public tracks: { items, page, size, totalItems, totalPages }. */
export const fetchPublicTracks = (query, page, size) => {
  const params = new URLSearchParams({ page, size });
  if (query.trim()) params.set("q", query.trim());
  return apiRequest(`/api/tracks/public?${params}`);
};

/** A track with its days, topics and files, or a 404 if the user cannot see it. */
export const fetchTrackDetail = (slug) =>
  apiRequest(`/api/tracks/by-slug/${encodeURIComponent(slug)}`);

/**
 * The quiz for one day, without its answers:
 * { trackId, topicId, quizId, questions: [{ questionId, question, options, sourceFile }] }.
 *
 * Opening a quiz never asks the model. If the day has no quiz yet, this fails
 * with a 404 and the message "The quiz for this day is not ready yet."
 */
export const fetchQuiz = (trackId, topicId) =>
  apiRequest(`/api/tracks/${trackId}/topics/${topicId}/quiz`);

/**
 * Makes the day's quiz now, if it has none, and returns it like fetchQuiz.
 * Owner only. The model writes it, which takes up to a minute and costs money.
 */
export const prepareQuiz = (trackId, topicId) =>
  apiRequest(`/api/tracks/${trackId}/topics/${topicId}/quiz`, {
    method: "POST",
  });

/**
 * Sends the answers and gets the score and the correct answers back:
 * { quizId, score, total, results: [{ questionId, chosenIndex, correctIndex, correct, explanation }] }.
 *
 * answers is [{ questionId, chosenIndex }], with chosenIndex null when the
 * time ran out.
 */
export const checkAnswers = (trackId, topicId, quizId, answers) =>
  apiRequest(`/api/tracks/${trackId}/topics/${topicId}/quiz/answers`, {
    method: "POST",
    body: { quizId, answers },
  });

/** Types a browser tab can show itself; anything else is downloaded. */
const VIEWABLE = /^(application\/pdf|image\/(png|jpeg|gif|webp)|text\/plain)$/;

/**
 * Opens a resource in a new tab, or downloads it when the browser cannot show it.
 *
 * The tab is opened before the file arrives: browsers only allow a new tab
 * straight from a click, and the fetch would push it past that moment.
 */
export const openResource = async (file) => {
  const viewable = VIEWABLE.test(file.contentType);
  const tab = viewable ? window.open("", "_blank") : null;
  try {
    const blob = await apiBlob(`/api/files/${file.fileId}`);
    const url = URL.createObjectURL(blob);
    if (tab) {
      tab.location.href = url;
    } else {
      const link = document.createElement("a");
      link.href = url;
      link.download = file.originalFilename;
      link.click();
    }
    // Long enough for the tab or download to read it.
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
  } catch (cause) {
    tab?.close();
    throw cause;
  }
};
