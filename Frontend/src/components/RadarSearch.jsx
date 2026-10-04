import { motion } from "motion/react";
import { spring } from "@/lib/motion";
import { useReducedMotion } from "@/lib/useReducedMotion";
/** Offsets from the centre, in viewBox units. */
const BLIPS = [
  { dx: 51, dy: -36 },
  { dx: -60, dy: 50 },
  { dx: -37, dy: -26 },
];
const ORIGIN = { transformOrigin: "100px 100px" };
/**
 * Search status, drawn.
 *
 * The sweep is a constant-velocity loop rather than a spring: springs are for
 * things the user can touch, and a radar sweep has no gesture behind it. One
 * turn every 2s (0.5Hz) deliberately avoids the ~0.2Hz band that reads as
 * queasy (§14). Only the state transitions use springs.
 */
const RadarSearch = ({ status }) => {
  const reduced = useReducedMotion();
  const searching = status === "searching";
  const found = status === "found";
  return (
    <svg
      aria-hidden
      viewBox="0 0 200 200"
      className="size-44 shrink-0 text-foreground"
    >
      {/* Structure: three fixed rings and a crosshair, so the graphic still
            reads as a radar when every animation is switched off. */}
      <g stroke="currentColor" fill="none">
        {[30, 60, 90].map((r) => (
          <circle key={r} cx="100" cy="100" r={r} strokeOpacity="0.14" />
        ))}
        <path d="M10 100h180M100 10v180" strokeOpacity="0.08" />
      </g>

      {!reduced && searching && (
        <>
          {/* Pulses travelling outward. Scale and opacity only, so this stays
                on the compositor (§11). */}
          {[0, 0.6, 1.2].map((delay) => (
            <motion.circle
              key={delay}
              cx="100"
              cy="100"
              r="90"
              fill="none"
              stroke="currentColor"
              style={ORIGIN}
              initial={{ scale: 0.12, opacity: 0 }}
              animate={{ scale: 1, opacity: [0, 0.35, 0] }}
              transition={{
                duration: 1.8,
                delay,
                repeat: Infinity,
                ease: "easeOut",
              }}
            />
          ))}

          <defs>
            <linearGradient
              id="radar-sweep-fill"
              gradientUnits="userSpaceOnUse"
              x1="100"
              y1="100"
              x2="190"
              y2="100"
            >
              <stop offset="0" stopColor="currentColor" stopOpacity="0" />
              <stop offset="1" stopColor="currentColor" stopOpacity="0.22" />
            </linearGradient>
          </defs>

          <g className="radar-sweep">
            <path
              d="M100 100 L190 100 A90 90 0 0 1 145 177.9 Z"
              fill="url(#radar-sweep-fill)"
            />
          </g>
        </>
      )}

      {/* Candidates. On a match the chosen one travels inward, so the motion
            says what happened before the text does (§8). */}
      {(searching || found) &&
        BLIPS.map(({ dx, dy }, index) => {
          const isMatch = index === 0;
          if (found && !isMatch) return null;
          return (
            <motion.circle
              key={`${dx},${dy}`}
              r="3.5"
              fill="currentColor"
              // cx/cy rather than x/y: on an SVG circle Motion writes x/y as
              // attributes, which a circle ignores, so the dot never moved.
              // Animating the attributes costs a layout per frame, which is
              // fine for one 3.5px dot travelling once.
              initial={{ cx: 100 + dx, cy: 100 + dy, opacity: 0 }}
              animate={
                found && isMatch
                  ? // Stops beside you, not on top of you, so the settled
                    // state still shows two participants.
                    { cx: 118, cy: 100, opacity: 1 }
                  : {
                      cx: 100 + dx,
                      cy: 100 + dy,
                      opacity: reduced ? 0.7 : [0, 0.7, 0.7, 0],
                    }
              }
              transition={
                found && isMatch
                  ? spring.default
                  : reduced
                    ? { duration: 0.2 }
                    : {
                        duration: 2.6,
                        delay: index * 0.5,
                        repeat: Infinity,
                        ease: "easeInOut",
                      }
              }
            />
          );
        })}

      {found && (
        <motion.line
          x1="100"
          y1="100"
          x2="118"
          y2="100"
          stroke="currentColor"
          strokeOpacity="0.4"
          initial={{ pathLength: 0 }}
          animate={{ pathLength: 1 }}
          transition={{ ...spring.default, delay: 0.08 }}
        />
      )}

      {/* You. */}
      <circle cx="100" cy="100" r="5" fill="currentColor" />
      <circle
        cx="100"
        cy="100"
        r="11"
        fill="none"
        stroke="currentColor"
        strokeOpacity="0.3"
      />
    </svg>
  );
};
export default RadarSearch;
