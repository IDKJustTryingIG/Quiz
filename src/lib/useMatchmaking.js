import { useCallback, useEffect, useRef, useState } from "react";
/**
 * SIMULATED matchmaking. There is no matchmaking service yet — this resolves a
 * timer against a fixed pool so the interface can be built and judged. The
 * returned shape is what a real socket would expose, so swapping the
 * implementation should not touch any component.
 */
const POOL = [
  { name: "ada_l", rating: 2032 },
  { name: "hexcodes", rating: 1790 },
  { name: "n_katherine", rating: 1902 },
  { name: "turing_test", rating: 1845 },
];
const FOUND_MIN_MS = 2200;
const FOUND_MAX_MS = 4400;
/** After this long, say so rather than spinning silently. */
export const WIDEN_AFTER_S = 30;
export const useMatchmaking = () => {
  const [status, setStatus] = useState("idle");
  const [opponent, setOpponent] = useState(null);
  const [elapsed, setElapsed] = useState(0);
  const timers = useRef([]);
  const clearTimers = useCallback(() => {
    timers.current.forEach((timer) => window.clearTimeout(timer));
    timers.current = [];
  }, []);
  useEffect(() => clearTimers, [clearTimers]);
  // Tick only while searching, so elapsed time is real rather than decorative.
  useEffect(() => {
    if (status !== "searching") return;
    const tick = window.setInterval(() => setElapsed((s) => s + 1), 1000);
    return () => window.clearInterval(tick);
  }, [status]);
  const start = useCallback(() => {
    clearTimers();
    setOpponent(null);
    setElapsed(0);
    setStatus("searching");
    const delay = FOUND_MIN_MS + Math.random() * (FOUND_MAX_MS - FOUND_MIN_MS);
    timers.current.push(
      window.setTimeout(() => {
        // Haptic fires with the state change, so the buzz lands on the same
        // frame as the visual rather than trailing it (§13 harmony). Reserved
        // for this one meaningful moment.
        navigator.vibrate?.(12);
        setOpponent(POOL[Math.floor(Math.random() * POOL.length)]);
        setStatus("found");
      }, delay),
    );
  }, [clearTimers]);
  const cancel = useCallback(() => {
    clearTimers();
    setStatus("idle");
    setOpponent(null);
    setElapsed(0);
  }, [clearTimers]);
  return { status, opponent, elapsed, start, cancel };
};
