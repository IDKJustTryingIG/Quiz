import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import { AnimatePresence, motion } from "motion/react";
import {
  CheckIcon,
  ChevronDownIcon,
  FileIcon,
  ListChecksIcon,
  LoaderIcon,
  PencilIcon,
  Trash2Icon,
} from "lucide-react";
import EditTrackDialog from "@/components/EditTrackDialog";
import { Button, buttonVariants } from "@/components/ui/button";
import { spring } from "@/lib/motion";
import { useAuth } from "@/lib/auth";
import {
  formatDay,
  useCurrentEnrollment,
  useEnrollment,
} from "@/lib/enrollment";
import { deleteTrack, fetchTrackDetail, openResource } from "@/lib/tracks";
import { useReducedMotion } from "@/lib/useReducedMotion";

const FileLink = ({ file }) => {
  const [opening, setOpening] = useState(false);
  const [error, setError] = useState(null);

  const open = async () => {
    setOpening(true);
    setError(null);
    try {
      await openResource(file);
    } catch (cause) {
      setError(cause.message);
    } finally {
      setOpening(false);
    }
  };

  return (
    <li>
      <button
        type="button"
        onClick={open}
        disabled={opening}
        className="flex max-w-full items-center gap-2 rounded-md px-2 py-1 text-left text-sm hover:bg-muted focus-visible:outline-2 focus-visible:outline-ring disabled:opacity-60"
      >
        {opening ? (
          <LoaderIcon className="size-4 shrink-0 animate-spin vibrant-secondary" />
        ) : (
          <FileIcon className="size-4 shrink-0 vibrant-secondary" />
        )}
        <span className="truncate underline-offset-4 hover:underline">
          {file.originalFilename}
        </span>
      </button>
      {error && (
        <p role="alert" className="px-2 text-xs text-destructive">
          {error}
        </p>
      )}
    </li>
  );
};

const DayItem = ({ slug, day, open, onToggle, isToday }) => {
  const reduced = useReducedMotion();
  const panelId = `day-${day.position}-files`;

  return (
    <li>
      <div className="flex items-stretch gap-2">
        <button
          type="button"
          onClick={onToggle}
          aria-expanded={open}
          aria-controls={panelId}
          className={`flex min-w-0 flex-1 items-center gap-4 rounded-xl border px-4 py-3 text-left transition-colors hover:border-foreground/25 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring ${isToday ? "border-foreground/40" : ""}`}
        >
          <span className="shrink-0 font-medium">Day {day.position + 1}</span>
          {isToday && (
            <span className="shrink-0 rounded-full bg-foreground px-2 py-0.5 text-xs font-medium text-background">
              Today
            </span>
          )}
          <span className="ml-auto min-w-0 truncate text-right">
            {day.topicName}
          </span>
          <ChevronDownIcon
            className={`size-4 shrink-0 transition-transform duration-200 ${open ? "rotate-180" : ""}`}
            aria-hidden="true"
          />
        </button>
        {/* The quiz is written from the day's files, so a day without files has none. */}
        {day.files.length > 0 ? (
          <Link
            to={`/tracks/${encodeURIComponent(slug)}/topics/${day.topicId}/quiz`}
            aria-label={`Quiz for Day ${day.position + 1}: ${day.topicName}`}
            className={buttonVariants({
              variant: "outline",
              className: "h-auto rounded-xl px-3",
            })}
          >
            <ListChecksIcon aria-hidden="true" />
            Quiz
          </Link>
        ) : (
          <Button variant="outline" disabled className="h-auto rounded-xl px-3">
            <ListChecksIcon aria-hidden="true" />
            Quiz
          </Button>
        )}
      </div>

      <AnimatePresence initial={false}>
        {open && (
          <motion.div
            id={panelId}
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: "auto", opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={reduced ? { duration: 0.15 } : spring.snappy}
            className="overflow-hidden"
          >
            <div className="py-2 pl-6">
              {day.files.length === 0 ? (
                <p className="px-2 py-1 text-sm vibrant-secondary">
                  No resources for this day.
                </p>
              ) : (
                <ul className="grid gap-0.5">
                  {day.files.map((file) => (
                    <FileLink key={file.fileId} file={file} />
                  ))}
                </ul>
              )}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </li>
  );
};

/**
 * Enroll, switch or leave. A user follows one track at a time, and Day 1 is
 * the day after they enroll. Questions sit in the page, not in a browser dialog.
 */
const EnrollPanel = ({ track }) => {
  const { status, enrollment } = useCurrentEnrollment();
  const enroll = useEnrollment((s) => s.enroll);
  const leave = useEnrollment((s) => s.leave);
  const setEmail = useEnrollment((s) => s.setEmail);
  // "leave" or "switch" while the user confirms, else null.
  const [confirming, setConfirming] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  if (status === "loading" || status === "idle") return null;

  // A finished track no longer counts: the user can enroll anywhere, here too.
  const active = enrollment?.progress === "FINISHED" ? null : enrollment;
  const here = active?.trackId === track.trackId;
  const elsewhere = active && !here;

  const run = async (action) => {
    setBusy(true);
    setError(null);
    try {
      await action();
      setConfirming(null);
    } catch (cause) {
      setError(cause.message);
    } finally {
      setBusy(false);
    }
  };

  let message;
  let actions;
  if (track.days.length === 0) {
    message = "You can enroll when this track has days.";
    actions = (
      <Button size="sm" disabled>
        Enroll
      </Button>
    );
  } else if (here) {
    message = (
      <>
        <span className="mr-2 inline-flex items-center gap-1 rounded-full bg-foreground px-2 py-0.5 text-xs font-medium text-background">
          <CheckIcon className="size-3" aria-hidden="true" />
          Enrolled
        </span>
        {active.progress === "NOT_STARTED"
          ? `Day 1 starts ${formatDay(active.startDate)}.`
          : `Today is Day ${active.dayNumber} of ${active.totalDays}: ${active.today.topicName}.`}
      </>
    );
    actions = (
      <Button
        variant="outline"
        size="sm"
        onClick={() => setConfirming("leave")}
        disabled={confirming !== null}
      >
        Leave
      </Button>
    );
  } else if (elsewhere) {
    message = (
      <>
        You follow{" "}
        <Link
          to={`/tracks/${encodeURIComponent(active.trackSlug)}`}
          className="font-medium underline underline-offset-4"
        >
          {active.trackName}
        </Link>
        . You can follow one track at a time.
      </>
    );
    actions = (
      <Button
        size="sm"
        onClick={() => setConfirming("switch")}
        disabled={confirming !== null}
      >
        Switch to this track
      </Button>
    );
  } else {
    message = "Enroll to get one topic a day. Day 1 starts tomorrow.";
    actions = (
      <Button
        size="sm"
        onClick={() => run(() => enroll(track.trackId))}
        disabled={busy}
      >
        {busy ? "Enrolling…" : "Enroll"}
      </Button>
    );
  }

  return (
    <div className="mb-6 grid gap-3 rounded-xl border p-4">
      <div className="flex flex-wrap items-center gap-3">
        <p className="min-w-0 flex-1 text-sm">{message}</p>
        <div className="shrink-0">{actions}</div>
      </div>

      {here && (
        <label className="flex items-center gap-2 border-t pt-3 text-sm">
          <input
            id="daily-email"
            type="checkbox"
            checked={active.emailOptIn}
            disabled={busy}
            onChange={(event) => {
              const enabled = event.target.checked;
              run(() => setEmail(track.trackId, enabled));
            }}
            className="size-4 accent-foreground"
          />
          Email me the topic at 6:00 AM each day
        </label>
      )}

      {confirming === "leave" && (
        <div
          role="alertdialog"
          aria-labelledby="leave-track-title"
          className="grid gap-3 border-t pt-3"
        >
          <p id="leave-track-title" className="text-sm">
            Leave “{track.name}”? If you enroll again later, you start again at
            Day 1.
          </p>
          <div className="flex flex-wrap gap-2">
            <Button
              variant="destructive"
              size="sm"
              onClick={() => run(() => leave(track.trackId))}
              disabled={busy}
            >
              {busy ? "Leaving…" : "Leave track"}
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={() => setConfirming(null)}
              disabled={busy}
            >
              Cancel
            </Button>
          </div>
        </div>
      )}

      {confirming === "switch" && (
        <div
          role="alertdialog"
          aria-labelledby="switch-track-title"
          className="grid gap-3 border-t pt-3"
        >
          <p id="switch-track-title" className="text-sm">
            Switch to “{track.name}”? You leave “{active.trackName}”, and Day 1
            here starts tomorrow.
          </p>
          <div className="flex flex-wrap gap-2">
            <Button
              size="sm"
              onClick={() =>
                run(() => enroll(track.trackId, { switchTrack: true }))
              }
              disabled={busy}
            >
              {busy ? "Switching…" : "Switch track"}
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={() => setConfirming(null)}
              disabled={busy}
            >
              Cancel
            </Button>
          </div>
        </div>
      )}

      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
    </div>
  );
};

/**
 * Asks the owner to confirm before the track is deleted. The question sits in
 * the page, under the title, rather than in a browser dialog.
 */
const DeleteConfirm = ({ track, onCancel, onDeleted }) => {
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState(null);
  const fileCount = track.days.reduce((sum, day) => sum + day.files.length, 0);

  const confirm = async () => {
    setDeleting(true);
    setError(null);
    try {
      await deleteTrack(track.trackId);
      onDeleted();
    } catch (cause) {
      setDeleting(false);
      setError(cause.message);
    }
  };

  return (
    <div
      role="alertdialog"
      aria-labelledby="delete-track-title"
      aria-describedby="delete-track-detail"
      className="mb-6 grid gap-3 rounded-xl border border-destructive/40 bg-destructive/5 p-4"
    >
      <p id="delete-track-title" className="font-medium">
        Delete “{track.name}”?
      </p>
      <p id="delete-track-detail" className="text-sm vibrant-secondary">
        This deletes {track.days.length}{" "}
        {track.days.length === 1 ? "day" : "days"} and {fileCount}{" "}
        {fileCount === 1 ? "file" : "files"}. You cannot undo this.
      </p>
      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
      <div className="flex flex-wrap gap-2">
        <Button variant="destructive" onClick={confirm} disabled={deleting}>
          {deleting ? "Deleting…" : "Delete track"}
        </Button>
        <Button variant="outline" onClick={onCancel} disabled={deleting}>
          Cancel
        </Button>
      </div>
    </div>
  );
};

const TrackDetail = () => {
  const { slug } = useParams();
  const navigate = useNavigate();
  const user = useAuth((s) => s.user);
  // Bumped after an edit, so the page loads the saved track again.
  const [reloadKey, setReloadKey] = useState(0);
  const [editOpen, setEditOpen] = useState(false);
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  // The result remembers its slug, so moving to another track never shows
  // the previous one's days while the new one loads.
  const [result, setResult] = useState({
    slug: null,
    track: null,
    error: null,
  });
  // Days the user has toggled away from their default (one day open),
  // kept per track so opening another track starts fresh.
  const [toggledFor, setToggledFor] = useState({ slug, days: new Set() });
  const toggled = toggledFor.slug === slug ? toggledFor.days : new Set();

  useEffect(() => {
    let current = true;
    fetchTrackDetail(slug)
      .then((track) => {
        if (current) setResult({ slug, track, error: null });
      })
      .catch((cause) => {
        if (current)
          setResult({
            slug,
            track: null,
            error:
              cause.status === 404
                ? "This track does not exist, or it is private."
                : cause.message,
          });
      });
    return () => {
      current = false;
    };
  }, [slug, reloadKey]);

  const loading = result.slug !== slug;
  const { track, error } = result;
  const isOwner = Boolean(track && user && user.userId === track.ownerId);
  const { enrollment } = useCurrentEnrollment();
  // The day to mark as today, if the user follows this track and it is under way.
  const todayPosition =
    track &&
    enrollment?.trackId === track.trackId &&
    enrollment.progress === "IN_PROGRESS"
      ? enrollment.dayNumber - 1
      : null;
  // Today's day starts open; without one, Day 1 does.
  const defaultOpen = todayPosition ?? 0;

  const isOpen = (position) =>
    (position === defaultOpen) !== toggled.has(position);
  const toggle = (position) =>
    setToggledFor((prev) => {
      const next = new Set(prev.slug === slug ? prev.days : []);
      if (next.has(position)) next.delete(position);
      else next.add(position);
      return { slug, days: next };
    });

  let body;
  if (loading) {
    body = <p className="text-sm vibrant-secondary">Loading…</p>;
  } else if (error) {
    body = (
      <p role="alert" className="text-sm text-destructive">
        {error}
      </p>
    );
  } else {
    body = (
      <>
        <div className="mb-6 flex flex-wrap items-start gap-3">
          <h1 className="min-w-0 flex-1 text-2xl font-semibold break-words">
            {track.name}
          </h1>
          {isOwner && (
            <div className="flex shrink-0 gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setEditOpen(true)}
              >
                <PencilIcon />
                Edit
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setConfirmingDelete(true)}
                disabled={confirmingDelete}
              >
                <Trash2Icon />
                Delete
              </Button>
            </div>
          )}
        </div>
        <EnrollPanel track={track} />
        {isOwner && confirmingDelete && (
          <DeleteConfirm
            track={track}
            onCancel={() => setConfirmingDelete(false)}
            onDeleted={() => navigate("/tracks", { replace: true })}
          />
        )}
        {track.days.length === 0 ? (
          <p className="text-sm vibrant-secondary">
            This track has no days yet.
          </p>
        ) : (
          <ol className="grid gap-3">
            {track.days.map((day) => (
              <DayItem
                key={day.topicId}
                slug={slug}
                day={day}
                open={isOpen(day.position)}
                onToggle={() => toggle(day.position)}
                isToday={day.position === todayPosition}
              />
            ))}
          </ol>
        )}
      </>
    );
  }

  return (
    <section className="mx-auto max-w-3xl px-4 py-16">
      <div className="rounded-2xl border p-5 sm:p-8">{body}</div>
      {isOwner && (
        <EditTrackDialog
          // A new key after each save, so the form starts from the saved track.
          key={`${track.trackId}-${reloadKey}`}
          track={track}
          open={editOpen}
          onOpenChange={setEditOpen}
          onSaved={() => setReloadKey((k) => k + 1)}
        />
      )}
    </section>
  );
};
export default TrackDetail;
