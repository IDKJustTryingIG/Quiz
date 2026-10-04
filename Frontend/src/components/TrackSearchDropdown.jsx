import { useEffect, useId, useRef, useState } from "react";
import { Link, useNavigate } from "react-router";
import { LoaderIcon, SearchIcon } from "lucide-react";
import { Input } from "@/components/ui/input";
import { fetchPublicTracks } from "@/lib/tracks";

const PAGE_SIZE = 5;
const SEARCH_DELAY_MS = 250;

/**
 * Searches public tracks as the user types and lists them in a dropdown, five
 * at a time. The last row, "Show more", fetches the next five from the server.
 */
const TrackSearchDropdown = ({ label }) => {
  const navigate = useNavigate();
  const id = useId();
  const listId = `${id}-results`;
  const root = useRef(null);

  const [query, setQuery] = useState("");
  const [open, setOpen] = useState(false);
  // Results remember the query they answer, so a stale list never shows.
  const [results, setResults] = useState({
    query: null,
    items: [],
    page: 0,
    totalPages: 0,
    error: null,
  });
  const [loadingMore, setLoadingMore] = useState(false);
  // Index of the highlighted row for keyboard use; items.length is "Show more".
  const [active, setActive] = useState(-1);

  const trimmed = query.trim();

  useEffect(() => {
    if (!trimmed) return;
    let current = true;
    const timer = setTimeout(() => {
      fetchPublicTracks(trimmed, 0, PAGE_SIZE)
        .then((page) => {
          if (current)
            setResults({
              query: trimmed,
              items: page.items,
              page: 0,
              totalPages: page.totalPages,
              error: null,
            });
        })
        .catch((cause) => {
          if (current)
            setResults({
              query: trimmed,
              items: [],
              page: 0,
              totalPages: 0,
              error: cause.message,
            });
        });
    }, SEARCH_DELAY_MS);
    return () => {
      current = false;
      clearTimeout(timer);
    };
  }, [trimmed]);

  // Close when a click lands anywhere outside the search box and its list.
  useEffect(() => {
    if (!open) return;
    const onPointerDown = (event) => {
      if (!root.current?.contains(event.target)) setOpen(false);
    };
    document.addEventListener("pointerdown", onPointerDown);
    return () => document.removeEventListener("pointerdown", onPointerDown);
  }, [open]);

  const fresh = results.query === trimmed;
  const items = fresh ? results.items : [];
  const hasMore = fresh && results.page < results.totalPages - 1;
  const showList = open && trimmed !== "";

  const showMore = async () => {
    if (loadingMore || !hasMore) return;
    setLoadingMore(true);
    const forQuery = results.query;
    try {
      const next = await fetchPublicTracks(forQuery, results.page + 1, PAGE_SIZE);
      setResults((prev) => {
        // The user typed something else while this was loading.
        if (prev.query !== forQuery) return prev;
        // A track created meanwhile shifts the pages by one; skip repeats.
        const seen = new Set(prev.items.map((t) => t.trackId));
        return {
          ...prev,
          items: [...prev.items, ...next.items.filter((t) => !seen.has(t.trackId))],
          page: next.page,
          totalPages: next.totalPages,
        };
      });
    } catch (cause) {
      setResults((prev) =>
        prev.query === forQuery ? { ...prev, error: cause.message } : prev,
      );
    } finally {
      setLoadingMore(false);
    }
  };

  const rowCount = items.length + (hasMore ? 1 : 0);

  const onKeyDown = (event) => {
    if (event.key === "Escape") {
      setOpen(false);
      return;
    }
    if (!showList || rowCount === 0) return;
    if (event.key === "ArrowDown") {
      event.preventDefault();
      setActive((i) => (i + 1) % rowCount);
    } else if (event.key === "ArrowUp") {
      event.preventDefault();
      setActive((i) => (i <= 0 ? rowCount - 1 : i - 1));
    } else if (event.key === "Enter" && active >= 0) {
      event.preventDefault();
      if (active < items.length) {
        setOpen(false);
        navigate(`/tracks/${items[active].slug}`);
      } else {
        showMore();
      }
    }
  };

  let status = null;
  if (!fresh) status = "Searching…";
  else if (results.error) status = results.error;
  else if (items.length === 0) status = `No tracks match “${trimmed}”.`;

  const optionId = (index) => `${id}-option-${index}`;

  return (
    <div ref={root} className="relative w-full sm:w-64">
      <label htmlFor={`${id}-input`} className="sr-only">
        {label}
      </label>
      <SearchIcon className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 vibrant-secondary" />
      <Input
        id={`${id}-input`}
        type="search"
        role="combobox"
        aria-expanded={showList}
        aria-controls={listId}
        aria-autocomplete="list"
        aria-activedescendant={
          showList && active >= 0 && active < rowCount ? optionId(active) : undefined
        }
        autoComplete="off"
        value={query}
        onChange={(event) => {
          setQuery(event.target.value);
          setOpen(true);
          setActive(-1);
        }}
        onFocus={() => setOpen(true)}
        onKeyDown={onKeyDown}
        placeholder="Search"
        className="pl-8"
      />

      {showList && (
        <div className="absolute top-full right-0 z-40 mt-1.5 w-full min-w-72 overflow-hidden rounded-xl border bg-popover text-popover-foreground shadow-lg">
          {status && (
            <p
              role={results.error && fresh ? "alert" : "status"}
              className={`px-3 py-2.5 text-sm ${results.error && fresh ? "text-destructive" : "vibrant-secondary"}`}
            >
              {status}
            </p>
          )}
          <ul
            id={listId}
            role="listbox"
            aria-label={label}
            className="max-h-80 overflow-y-auto"
          >
            {items.map((track, index) => (
              <li
                key={track.trackId}
                id={optionId(index)}
                role="option"
                aria-selected={active === index}
              >
                <Link
                  to={`/tracks/${track.slug}`}
                  tabIndex={-1}
                  onClick={() => setOpen(false)}
                  onPointerEnter={() => setActive(index)}
                  className={`block px-3 py-2 ${active === index ? "bg-muted" : ""}`}
                >
                  <span className="block truncate text-sm font-medium">
                    {track.name}
                  </span>
                  <span className="block truncate text-xs vibrant-secondary">
                    {track.topicCount} {track.topicCount === 1 ? "day" : "days"} ·
                    by {track.ownerUsername}
                  </span>
                </Link>
              </li>
            ))}
            {hasMore && (
              <li
                id={optionId(items.length)}
                role="option"
                aria-selected={active === items.length}
              >
                <button
                  type="button"
                  tabIndex={-1}
                  onClick={showMore}
                  onPointerEnter={() => setActive(items.length)}
                  disabled={loadingMore}
                  className={`flex w-full items-center justify-center gap-2 border-t px-3 py-2 text-sm font-medium ${active === items.length ? "bg-muted" : ""}`}
                >
                  {loadingMore && <LoaderIcon className="size-3.5 animate-spin" />}
                  {loadingMore ? "Loading…" : "Show more"}
                </button>
              </li>
            )}
          </ul>
        </div>
      )}
    </div>
  );
};

export default TrackSearchDropdown;
