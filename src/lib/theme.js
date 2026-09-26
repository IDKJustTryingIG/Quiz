import { create } from "zustand";

export const THEME_STORAGE_KEY = "quiz-theme";

const readStored = () => {
  try {
    const stored = localStorage.getItem(THEME_STORAGE_KEY);
    return stored === "light" || stored === "dark" ? stored : null;
  } catch {
    return null;
  }
};

const systemTheme = () =>
  window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";

/**
 * Whether they had already chosen a theme when the app loaded.
 *
 * Captured up front, before anything below writes to storage. Checking later
 * would always say yes, because applying a theme saves it.
 */
const chosenBefore = readStored();

/** Puts the theme on the page and remembers it for next time. */
const applyTheme = (theme) => {
  const root = document.documentElement;
  root.classList.toggle("dark", theme === "dark");
  root.style.colorScheme = theme;
  try {
    localStorage.setItem(THEME_STORAGE_KEY, theme);
  } catch {
    // Storage unavailable (private window, blocked cookies) — the theme still
    // applies for this session, it just will not be remembered.
  }
};

/**
 * Light or dark, for the whole app.
 *
 * Read one value at a time, so a component only re-renders when the bit it
 * actually uses changes:
 *
 *   const theme = useTheme((s) => s.theme);
 */
export const useTheme = create((set, get) => ({
  theme: chosenBefore ?? systemTheme(),

  setTheme: (theme) => {
    // Applied here rather than in an effect: changing the theme *is* the act of
    // putting it on the page, so there is nothing to react to afterwards.
    applyTheme(theme);
    set({ theme });
  },

  toggleTheme: () => get().setTheme(get().theme === "dark" ? "light" : "dark"),
}));

// The inline script in index.html already did this before the first paint, so
// this is only to keep the page in step with the store on load.
applyTheme(useTheme.getState().theme);

// Follow the operating system, but only until they pick a theme themselves.
if (!chosenBefore) {
  window
    .matchMedia("(prefers-color-scheme: dark)")
    .addEventListener("change", (event) =>
      useTheme.getState().setTheme(event.matches ? "dark" : "light"),
    );
}
