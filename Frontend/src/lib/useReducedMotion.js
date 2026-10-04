import { useEffect, useState } from "react";
const QUERY = "(prefers-reduced-motion: reduce)";
/**
 * Reduced motion does not mean no feedback — it means a gentler, non-vestibular
 * equivalent. Components read this to swap springs and slides for cross-fades.
 */
export const useReducedMotion = () => {
  const [reduced, setReduced] = useState(
    () => typeof window !== "undefined" && window.matchMedia(QUERY).matches,
  );
  useEffect(() => {
    const query = window.matchMedia(QUERY);
    const onChange = () => setReduced(query.matches);
    query.addEventListener("change", onChange);
    return () => query.removeEventListener("change", onChange);
  }, []);
  return reduced;
};
