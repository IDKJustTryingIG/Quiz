import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { animate, motion, useMotionValue, useTransform } from "motion/react";
import { useReducedMotion } from "@/lib/useReducedMotion";
import { nearestSnapPoint, project, rubberband, spring } from "@/lib/motion";
/** Movement before a drag commits, so taps on links still register (§10). */
const HYSTERESIS = 10;
/**
 * A bottom sheet that tracks the pointer 1:1, resists past its open position,
 * projects momentum on release, and can be grabbed mid-flight and reversed.
 *
 * The panel stays mounted and is translated offscreen rather than unmounted,
 * so an interrupted animation always has a live presentation value to resume
 * from — the thing that makes interruption look continuous instead of jumpy.
 */
const GestureSheet = ({ open, onOpenChange, label, children }) => {
  const y = useMotionValue(0);
  const panelRef = useRef(null);
  const [height, setHeight] = useState(0);
  const reduced = useReducedMotion();
  const controls = useRef(null);
  const dragging = useRef(false);
  const pending = useRef(null);
  const grabOffset = useRef(0);
  const history = useRef([]);
  const settled = useRef(false);
  useLayoutEffect(() => {
    const panel = panelRef.current;
    if (!panel) return;
    const measure = () => setHeight(panel.offsetHeight);
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(panel);
    return () => observer.disconnect();
  }, []);
  // Drive the sheet toward its resting place whenever `open` changes.
  useEffect(() => {
    if (!height) return;
    // First measurement: place it without animating, or it slides in on load.
    if (!settled.current) {
      settled.current = true;
      y.set(open ? 0 : height);
      return;
    }
    if (dragging.current) return;
    if (reduced) {
      y.set(open ? 0 : height);
      return;
    }
    controls.current = animate(y, open ? 0 : height, spring.sheet);
    return () => controls.current?.stop();
  }, [open, height, reduced, y]);
  // Scrim tracks the sheet continuously, so dragging down visibly telegraphs
  // dismissal rather than waiting for the gesture to finish (§1, §8).
  // The sheet casts its shadow upward, which bleeds into the viewport while it
  // sits offscreen — so take it out of rendering entirely once fully dismissed.
  const visibility = useTransform(y, (value) =>
    height && value >= height - 0.5 ? "hidden" : "visible",
  );
  // Interactivity must follow what is actually on screen, not the `open` prop.
  // Keying `inert` to `open` made the panel unreachable the instant a dismissal
  // began, so it could never be caught and pulled back mid-flight (§3).
  const [presented, setPresented] = useState(open);
  useEffect(
    () =>
      y.on("change", (value) => setPresented(!height || value < height - 0.5)),
    [y, height],
  );
  const scrimOpacity = useTransform(y, (value) =>
    height ? Math.max(0, Math.min(1, 1 - value / height)) : 0,
  );
  const velocity = () => {
    // Measured against the release moment, not the last sample: if the finger
    // rests before lifting, no further pointermove fires, and reusing those
    // stale samples would throw the sheet at a speed the user is no longer
    // moving. A pause must read as zero velocity.
    const now = performance.now();
    const samples = history.current.filter((sample) => now - sample.t <= 100);
    if (samples.length < 2) return 0;
    const first = samples[0];
    const last = samples[samples.length - 1];
    const dt = (last.t - first.t) / 1000;
    return dt > 0 ? (last.y - first.y) / dt : 0;
  };
  const onPointerDown = (event) => {
    // Grabbing mid-flight stops the spring; the drag then continues from
    // whatever is on screen right now, never from the logical target (§3).
    controls.current?.stop();
    pending.current = { startY: event.clientY, pointerId: event.pointerId };
    history.current = [{ y: event.clientY, t: performance.now() }];
  };
  const onPointerMove = (event) => {
    history.current.push({ y: event.clientY, t: performance.now() });
    if (history.current.length > 6) history.current.shift();
    if (!dragging.current) {
      const start = pending.current;
      if (!start) return;
      if (Math.abs(event.clientY - start.startY) < HYSTERESIS) return;
      dragging.current = true;
      event.currentTarget.setPointerCapture(start.pointerId);
      // Respect where they grabbed it — snapping to centre breaks the illusion.
      grabOffset.current = start.startY - y.get();
    }
    const raw = event.clientY - grabOffset.current;
    // Nothing above the open position, so resist progressively instead of
    // stopping dead (§9).
    y.set(raw < 0 ? rubberband(raw, height) : raw);
  };
  const endDrag = (event) => {
    pending.current = null;
    if (!dragging.current) return;
    dragging.current = false;
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
    const releaseVelocity = velocity();
    // Animate to where the flick was going, not to the nearest edge from where
    // the finger happened to stop (§6).
    const projected = y.get() + project(releaseVelocity);
    const target = nearestSnapPoint(projected, [0, height]);
    onOpenChange(target === 0);
    // Hand the release velocity to the spring so there is no seam between
    // dragging and animating (§5).
    controls.current = animate(y, target, {
      ...spring.sheet,
      velocity: releaseVelocity,
    });
  };
  useEffect(() => {
    if (!open) return;
    const onKeyDown = (event) => {
      if (event.key === "Escape") onOpenChange(false);
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [open, onOpenChange]);
  return (
    <>
      <motion.div
        aria-hidden
        onPointerDown={() => onOpenChange(false)}
        style={{ opacity: scrimOpacity }}
        className="fixed inset-0 z-40 bg-black/35"
        // Dim to focus, but never intercept input while dismissed.
        inert={!presented || undefined}
      />

      <motion.div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-label={label}
        inert={!presented || undefined}
        style={{ y, visibility, touchAction: "none" }}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={endDrag}
        onPointerCancel={endDrag}
        className="material-sheet fixed inset-x-0 bottom-0 z-50 rounded-t-3xl pb-[env(safe-area-inset-bottom)]"
      >
        <div className="flex cursor-grab justify-center py-3 active:cursor-grabbing">
          <span className="h-1 w-9 rounded-full bg-foreground/25" />
        </div>
        <div className="px-5 pb-8">{children}</div>
      </motion.div>
    </>
  );
};
export default GestureSheet;
