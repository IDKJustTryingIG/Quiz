import { useEffect, useRef, useState } from "react";

/** The bar turns red when this many milliseconds are left. */
const WARN_AT = 3000;

/**
 * Counts down once, from seconds to zero, and calls onExpire at zero.
 *
 * It never resumes: when running turns false the bar freezes where it is.
 * Give each question its own key so the next question starts a new timer.
 */
const QuizTimer = ({ seconds, running, onExpire }) => {
  const total = seconds * 1000;
  const [left, setLeft] = useState(total);
  // Kept in a ref so a new onExpire on each render does not restart the clock.
  const expire = useRef(onExpire);
  useEffect(() => {
    expire.current = onExpire;
  });

  useEffect(() => {
    if (!running) return;
    // Measured against a deadline, not by counting ticks, because the browser
    // delays intervals in background tabs.
    const deadline = performance.now() + total;
    const id = setInterval(() => {
      const remaining = Math.max(0, deadline - performance.now());
      setLeft(remaining);
      if (remaining === 0) {
        clearInterval(id);
        expire.current();
      }
    }, 100);
    return () => clearInterval(id);
  }, [running, total]);

  const warn = left <= WARN_AT;

  return (
    <div
      role="timer"
      aria-label={`${Math.ceil(left / 1000)} seconds left`}
      className="flex items-center gap-3"
    >
      <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-muted">
        <div
          className={`h-full origin-left rounded-full transition-[transform,background-color] duration-100 ease-linear motion-reduce:transition-none ${warn ? "bg-destructive" : "bg-foreground"}`}
          style={{ transform: `scaleX(${left / total})` }}
        />
      </div>
      <span
        aria-hidden="true"
        className={`w-8 text-right text-sm font-medium tabular-nums ${warn ? "text-destructive" : "vibrant-secondary"}`}
      >
        {Math.ceil(left / 1000)}s
      </span>
    </div>
  );
};
export default QuizTimer;
