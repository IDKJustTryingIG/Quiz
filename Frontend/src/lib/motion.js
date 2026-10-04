/**
 * Apple describes springs as damping ratio + response (seconds), deliberately
 * replacing mass/stiffness/damping. Motion's `bounce` + `duration` map onto
 * that pair: bounce 0 is critically damped (damping 1.0), and `duration` is
 * the response — how fast it reaches the target — not a fixed runtime.
 *
 * House rule: critically damped everywhere by default. Bounce is reserved for
 * motion the user's own gesture threw, because overshoot on something that
 * merely appeared reads as wrong.
 */
export const spring = {
  /** damping 1.0 / response 0.4 — anything not thrown by a gesture. */
  default: { type: "spring", bounce: 0, duration: 0.4 },
  /** damping 1.0 / response 0.3 — small chrome that should feel immediate. */
  snappy: { type: "spring", bounce: 0, duration: 0.3 },
  /** damping ~0.8 / response 0.4 — after a flick or throw. */
  momentum: { type: "spring", bounce: 0.2, duration: 0.4 },
  /** damping ~0.8 / response 0.3 — drawers and sheets. */
  sheet: { type: "spring", bounce: 0.2, duration: 0.3 },
};
/**
 * Where a flick would come to rest, using the exponential-decay form from
 * Apple's Designing Fluid Interfaces sample code. Deliberately not the
 * textbook v²/(2·decel) — that is not what iOS ships and it feels wrong.
 */
export const project = (velocity, decelerationRate = 0.998) =>
  ((velocity / 1000) * decelerationRate) / (1 - decelerationRate);
/**
 * Progressive resistance past a boundary. The further past the edge, the less
 * the element follows, so a limit reads as "nothing more here" rather than
 * "frozen".
 */
export const rubberband = (overshoot, dimension, constant = 0.55) =>
  (overshoot * dimension * constant) /
  (dimension + constant * Math.abs(overshoot));
/** Picks the snap point nearest a projected resting position. */
export const nearestSnapPoint = (projected, points) =>
  points.reduce((best, point) =>
    Math.abs(point - projected) < Math.abs(best - projected) ? point : best,
  );
