import { useEffect, useState } from "react";
import { Link } from "react-router";
import { AnimatePresence, motion } from "motion/react";
import {
  ChevronLeftIcon,
  ChevronRightIcon,
  LockIcon,
  PlusIcon,
  SearchIcon,
} from "lucide-react";
import AddTrackDialog from "@/components/AddTrackDialog";
import TodayCard from "@/components/TodayCard";
import TrackSearchDropdown from "@/components/TrackSearchDropdown";
import PageHeader from "@/components/PageHeader";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { spring } from "@/lib/motion";
import { fetchPublicTracks, fetchTracks } from "@/lib/tracks";
import { useReducedMotion } from "@/lib/useReducedMotion";

const SEARCH_DELAY_MS = 250;

/**
 * Bordered like SurfaceCard, but stacked so the cards sit in a grid, with
 * the author pinned to the bottom edge however long the name runs.
 */
const TrackCard = ({ track }) => (
  <Link
    to={`/tracks/${track.slug}`}
    className="flex h-full flex-col rounded-2xl border p-5 transition-[transform,border-color] duration-100 ease-out will-change-transform hover:border-foreground/25 active:scale-[0.985] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
  >
    <span className="line-clamp-2 font-medium break-words">{track.name}</span>
    {track.description && (
      <span className="mt-1 line-clamp-3 text-sm vibrant-secondary">
        {track.description}
      </span>
    )}
    <span className="mt-3 mb-4 flex items-center gap-1.5 text-xs vibrant-secondary">
      {track.topicCount} {track.topicCount === 1 ? "day" : "days"}
      {track.visibility === "PRIVATE" && (
        <>
          <span aria-hidden="true">·</span>
          <LockIcon className="size-3" aria-hidden="true" />
          Private
        </>
      )}
    </span>
    <span className="mt-auto truncate border-t pt-3 text-xs vibrant-secondary">
      Created by{" "}
      <span className="font-medium text-foreground">
        {track.ownerUsername}
      </span>
    </span>
  </Link>
);

const WIDE = "(min-width: 1024px)";
const MEDIUM = "(min-width: 640px)";

const columnsNow = () =>
  window.matchMedia(WIDE).matches ? 3 : window.matchMedia(MEDIUM).matches ? 2 : 1;

/** How many cards fit in one row at the current width: 1, 2 or 3. */
const useColumns = () => {
  const [columns, setColumns] = useState(columnsNow);
  useEffect(() => {
    const queries = [WIDE, MEDIUM].map((q) => window.matchMedia(q));
    const onChange = () => setColumns(columnsNow());
    queries.forEach((q) => q.addEventListener("change", onChange));
    return () =>
      queries.forEach((q) => q.removeEventListener("change", onChange));
  }, []);
  return columns;
};

/**
 * The user's own tracks, searched on the server as they type.
 * Bump `reloadKey` to fetch again, e.g. after a track is added.
 */
const useYourTracks = (reloadKey) => {
  const [query, setQuery] = useState("");
  // Results remember which request they answer; until the latest one lands,
  // the old results stay on screen and the section counts as loading.
  const [result, setResult] = useState({ key: null, tracks: [], error: null });
  const key = `${query}|${reloadKey}`;

  useEffect(() => {
    let current = true;
    const timer = setTimeout(() => {
      fetchTracks("mine", query)
        .then((tracks) => {
          if (current) setResult({ key, tracks, error: null });
        })
        .catch((cause) => {
          if (current) setResult({ key, tracks: [], error: cause.message });
        });
    }, SEARCH_DELAY_MS);
    // A slower, older search must not overwrite a newer one.
    return () => {
      current = false;
      clearTimeout(timer);
    };
  }, [key, query]);

  return {
    query,
    setQuery,
    tracks: result.tracks,
    error: result.error,
    loading: result.key !== key,
  };
};

/**
 * Public tracks, one row at a time. Each row is a page fetched from the
 * server, sized to the number of columns on screen. Searching happens in the
 * dropdown, so this always pages through every public track.
 */
const usePublicTracks = (reloadKey) => {
  const columns = useColumns();
  // Remember the first card shown rather than a page number, so resizing
  // from 3 columns to 2 keeps the same cards in view instead of jumping.
  const [firstIndex, setFirstIndex] = useState(0);
  // -1 back, 1 forward, 0 for anything that is not paging (e.g. resize).
  const [direction, setDirection] = useState(0);
  const page = Math.floor(firstIndex / columns);
  const key = `${page}|${columns}|${reloadKey}`;
  const [result, setResult] = useState({ key: null, data: null, error: null });

  useEffect(() => {
    let current = true;
    fetchPublicTracks("", page, columns)
        .then((data) => {
          if (!current) return;
          // Tracks went away since this page was chosen: settle on the last
          // page that still exists rather than show an empty row.
          if (data.items.length === 0 && page > 0 && data.totalPages > 0) {
            setFirstIndex((data.totalPages - 1) * columns);
            return;
          }
          setResult({ key, data, error: null });
        })
        .catch((cause) => {
          if (current) setResult({ key, data: null, error: cause.message });
        });
    return () => {
      current = false;
    };
  }, [key, page, columns]);

  const goTo = (nextPage) => {
    setDirection(nextPage > page ? 1 : -1);
    setFirstIndex(nextPage * columns);
  };

  return {
    page,
    goTo,
    direction,
    data: result.data,
    shownKey: result.key,
    error: result.error,
    loading: result.key !== key,
  };
};

/** The message shown in place of cards, or null when there are cards. */
const statusMessage = ({ error, loading, count, query, emptyText }) => {
  if (error)
    return (
      <p role="alert" className="text-sm text-destructive">
        {error}
      </p>
    );
  if (count > 0) return null;
  if (loading) return <p className="text-sm vibrant-secondary">Loading…</p>;
  return (
    <p className="text-sm vibrant-secondary">
      {query.trim() ? `No tracks match “${query.trim()}”.` : emptyText}
    </p>
  );
};

/** Filters the list below it as the user types. */
const SearchInput = ({ label, value, onChange }) => {
  const id = `search-${label.replace(/\s+/g, "-").toLowerCase()}`;
  return (
    <div className="relative w-full sm:w-64">
      <label htmlFor={id} className="sr-only">
        {label}
      </label>
      <SearchIcon className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 vibrant-secondary" />
      <Input
        id={id}
        type="search"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder="Search"
        className="pl-8"
      />
    </div>
  );
};

const TrackSection = ({ title, search, action, children }) => (
  <section className="rounded-2xl border p-5 sm:p-6">
    <div className="mb-5 flex flex-wrap items-center gap-3">
      <h2 className="mr-auto text-xl font-semibold">{title}</h2>
      {search}
      {action}
    </div>
    {children}
  </section>
);

const TopTracks = ({ reloadKey }) => {
  const reduced = useReducedMotion();
  const top = usePublicTracks(reloadKey);
  const items = top.data?.items ?? [];
  const totalPages = top.data?.totalPages ?? 0;
  const shownPage = top.data?.page ?? 0;

  const status = statusMessage({
    error: top.error,
    loading: top.loading,
    count: items.length,
    query: "",
    emptyText: "No public tracks yet.",
  });

  // Slide in from the side the user is heading; a plain fade for resizes,
  // reloads, or when reduced motion is on.
  const offset = reduced ? 0 : top.direction * 48;
  // Functions of `custom`, so the row leaving reads the latest direction
  // rather than the one from when it was drawn.
  const variants = {
    enter: (o) => ({ opacity: 0, x: o }),
    center: { opacity: 1, x: 0 },
    exit: (o) => ({ opacity: 0, x: -o }),
  };

  return (
    <TrackSection
      title="Top tracks"
      search={<TrackSearchDropdown label="Search top tracks" />}
    >
      {status ?? (
        <>
          <div className="flex items-center gap-2 sm:gap-3">
            <Button
              variant="outline"
              size="icon"
              className="shrink-0 rounded-full"
              onClick={() => top.goTo(top.page - 1)}
              disabled={top.page === 0}
              aria-label="Previous tracks"
            >
              <ChevronLeftIcon />
            </Button>

            <div className="relative min-w-0 flex-1 overflow-hidden p-1">
              <AnimatePresence
                mode="popLayout"
                initial={false}
                custom={offset}
              >
                <motion.ul
                  key={top.shownKey}
                  custom={offset}
                  variants={variants}
                  initial="enter"
                  animate="center"
                  exit="exit"
                  transition={reduced ? { duration: 0.15 } : spring.snappy}
                  className="grid w-full gap-4 sm:grid-cols-2 lg:grid-cols-3"
                  aria-busy={top.loading}
                >
                  {items.map((track) => (
                    <li key={track.trackId}>
                      <TrackCard track={track} />
                    </li>
                  ))}
                </motion.ul>
              </AnimatePresence>
            </div>

            <Button
              variant="outline"
              size="icon"
              className="shrink-0 rounded-full"
              onClick={() => top.goTo(top.page + 1)}
              disabled={top.page >= totalPages - 1}
              aria-label="Next tracks"
            >
              <ChevronRightIcon />
            </Button>
          </div>

          {totalPages > 1 && (
            <p
              className="mt-4 text-center text-xs vibrant-secondary"
              aria-live="polite"
            >
              Page {shownPage + 1} of {totalPages}
            </p>
          )}
        </>
      )}
    </TrackSection>
  );
};

const YourTracks = ({ reloadKey, onAdd }) => {
  const yours = useYourTracks(reloadKey);
  const status = statusMessage({
    error: yours.error,
    loading: yours.loading,
    count: yours.tracks.length,
    query: yours.query,
    emptyText: "You have not made a track yet. Add one to get started.",
  });

  return (
    <TrackSection
      title="Your tracks"
      search={
        <SearchInput
          label="Search your tracks"
          value={yours.query}
          onChange={yours.setQuery}
        />
      }
      action={
        <Button onClick={onAdd}>
          <PlusIcon />
          Add Track
        </Button>
      }
    >
      {status ?? (
        <ul
          className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3"
          aria-busy={yours.loading}
        >
          {yours.tracks.map((track) => (
            <li key={track.trackId}>
              <TrackCard track={track} />
            </li>
          ))}
        </ul>
      )}
    </TrackSection>
  );
};

const Tracks = () => {
  const [dialogOpen, setDialogOpen] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);

  return (
    <section className="mx-auto max-w-5xl px-4 py-16">
      <PageHeader
        title="All tracks"
        description="Each track is ordered, so every topic assumes only what came before it. Work down the list."
      />
      <TodayCard />
      <div className="grid gap-6">
        <TopTracks reloadKey={reloadKey} />
        <YourTracks
          reloadKey={reloadKey}
          onAdd={() => setDialogOpen(true)}
        />
      </div>
      <AddTrackDialog
        open={dialogOpen}
        onOpenChange={setDialogOpen}
        onCreated={() => setReloadKey((k) => k + 1)}
      />
    </section>
  );
};
export default Tracks;
