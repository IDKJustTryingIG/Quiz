import { useEffect, useState } from "react";
import { useParams } from "react-router";
import { AnimatePresence, motion } from "motion/react";
import { ChevronDownIcon, FileIcon, LoaderIcon } from "lucide-react";
import { spring } from "@/lib/motion";
import { fetchTrackDetail, openResource } from "@/lib/tracks";
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

const DayItem = ({ day, open, onToggle }) => {
  const reduced = useReducedMotion();
  const panelId = `day-${day.position}-files`;

  return (
    <li>
      <button
        type="button"
        onClick={onToggle}
        aria-expanded={open}
        aria-controls={panelId}
        className="flex w-full items-center gap-4 rounded-xl border px-4 py-3 text-left transition-colors hover:border-foreground/25 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
      >
        <span className="shrink-0 font-medium">Day {day.position + 1}</span>
        <span className="ml-auto min-w-0 truncate text-right">
          {day.topicName}
        </span>
        <ChevronDownIcon
          className={`size-4 shrink-0 transition-transform duration-200 ${open ? "rotate-180" : ""}`}
          aria-hidden="true"
        />
      </button>

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

const TrackDetail = () => {
  const { slug } = useParams();
  // The result remembers its slug, so moving to another track never shows
  // the previous one's days while the new one loads.
  const [result, setResult] = useState({ slug: null, track: null, error: null });
  // Days the user has toggled away from their default (only Day 1 open),
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
  }, [slug]);

  const loading = result.slug !== slug;
  const { track, error } = result;

  const isOpen = (position) => (position === 0) !== toggled.has(position);
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
        <h1 className="mb-6 text-2xl font-semibold break-words">
          {track.name}
        </h1>
        {track.days.length === 0 ? (
          <p className="text-sm vibrant-secondary">This track has no days yet.</p>
        ) : (
          <ol className="grid gap-3">
            {track.days.map((day) => (
              <DayItem
                key={day.topicId}
                day={day}
                open={isOpen(day.position)}
                onToggle={() => toggle(day.position)}
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
    </section>
  );
};
export default TrackDetail;
