import { useCallback, useEffect } from "react";
import { AnimatePresence, motion } from "motion/react";
import PageHeader from "@/components/PageHeader";
import RadarSearch from "@/components/RadarSearch";
import { spring } from "@/lib/motion";
import { WIDEN_AFTER_S, useMatchmaking } from "@/lib/useMatchmaking";
// Placeholder content — replace with real standings from the API.
const standings = [
  { rank: 1, name: "wintermute", rating: 2140 },
  { rank: 2, name: "ada_l", rating: 2032 },
  { rank: 3, name: "you", rating: 1884 },
  { rank: 4, name: "hexcodes", rating: 1790 },
];
const YOUR_RATING = 1884;
const Battle = () => {
  const { status, opponent, elapsed, start, cancel } = useMatchmaking();
  // A callback ref, not an effect: AnimatePresence in "wait" mode keeps the
  // new content unmounted until the old has exited, so an effect fires while
  // the button does not exist yet. Stable identity, so the per-second elapsed
  // re-render cannot steal focus back.
  const focusOnMount = useCallback((node) => {
    node?.focus();
  }, []);
  // Escape always works while waiting — never trap someone in a waiting state.
  useEffect(() => {
    if (status !== "searching") return;
    const onKeyDown = (event) => {
      if (event.key === "Escape") cancel();
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [status, cancel]);
  const searchLine =
    elapsed >= WIDEN_AFTER_S
      ? "Still looking. Widening the rating range."
      : `Looking for an opponent near ${YOUR_RATING}.`;
  return (
    <section className="mx-auto max-w-5xl px-4 py-16">
      <PageHeader
        title="Battle"
        description="You and one other coder get the same problem at the same moment. First correct answer takes the round."
      />

      {/* The card grows in place rather than opening a modal, so the search
            stays anchored to the control that started it (§7) and the standings
            below stay readable (§12: separate to keep flow). */}
      <motion.div
        layout
        transition={spring.default}
        className="overflow-hidden rounded-2xl border"
      >
        <AnimatePresence mode="wait" initial={false}>
          {status === "idle" && (
            <motion.div
              key="idle"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.15 }}
            >
              <button
                type="button"
                onClick={start}
                className="w-full p-6 text-left transition-transform duration-100 ease-out active:scale-[0.99] focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-ring"
              >
                <span className="block text-xl font-medium">
                  Find an opponent
                </span>
                <span className="mt-1 block text-sm vibrant-secondary">
                  Matched near your rating · usually under 30 seconds
                </span>
              </button>
            </motion.div>
          )}

          {(status === "searching" || status === "found") && (
            <motion.div
              key="search"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.15 }}
              className="flex flex-col items-center gap-6 p-8 sm:flex-row sm:gap-8 sm:p-10"
            >
              <RadarSearch status={status} />

              <div className="min-w-0 flex-1 text-center sm:text-left">
                <p role="status" className="text-xl font-medium text-balance">
                  {status === "found" && opponent
                    ? `Matched with ${opponent.name}`
                    : searchLine}
                </p>
                <p className="mt-1 text-sm vibrant-secondary tabular-nums">
                  {status === "found" && opponent
                    ? `Rating ${opponent.rating}`
                    : elapsed >= 5
                      ? `${elapsed}s`
                      : " "}
                </p>

                <div className="mt-6 flex flex-wrap justify-center gap-2 sm:justify-start">
                  {status === "found" ? (
                    <>
                      <button
                        type="button"
                        onClick={cancel}
                        className="rounded-full bg-foreground px-5 py-2.5 text-sm font-medium text-background transition-transform duration-100 ease-out active:scale-[0.97] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
                      >
                        Start round
                      </button>
                      <button
                        type="button"
                        onClick={start}
                        className="rounded-full border px-5 py-2.5 text-sm font-medium transition-[transform,border-color] duration-100 ease-out hover:border-foreground/25 active:scale-[0.97] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
                      >
                        Find someone else
                      </button>
                    </>
                  ) : (
                    <button
                      ref={focusOnMount}
                      type="button"
                      onClick={cancel}
                      className="rounded-full border px-5 py-2.5 text-sm font-medium transition-[transform,border-color] duration-100 ease-out hover:border-foreground/25 active:scale-[0.97] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
                    >
                      Cancel
                    </button>
                  )}
                </div>

                {status === "found" && (
                  <p className="mt-3 text-xs vibrant-secondary">
                    Rounds are not built yet, so this returns you to the start.
                  </p>
                )}
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      </motion.div>

      <h2 className="mt-14 mb-4 text-sm font-medium vibrant-secondary">
        This week
      </h2>
      <ol className="grid overflow-hidden rounded-2xl border">
        {standings.map(({ rank, name, rating }) => (
          <li
            key={rank}
            className={`flex items-baseline gap-4 border-b px-5 py-4 last:border-b-0 ${name === "you" ? "font-medium" : ""}`}
          >
            <span className="w-5 text-sm tabular-nums vibrant-secondary">
              {rank}
            </span>
            <span className="flex-1">{name}</span>
            <span className="text-sm tabular-nums vibrant-secondary">
              {rating}
            </span>
          </li>
        ))}
      </ol>
    </section>
  );
};
export default Battle;
