import { create } from "zustand";
import { apiRequest, refreshSession, setAccessToken } from "@/lib/api";

/**
 * Signing up and logging in do the same thing: send some details, get an access
 * token back in the body and a refresh cookie in the headers.
 *
 * noRetry says "do not try to fix this by refreshing". A rejection here means
 * the details were wrong, so asking again would only be told the same thing.
 */
const startSession = async (path, body) => {
  const tokens = await apiRequest(path, {
    method: "POST",
    body,
    noRetry: true,
  });
  setAccessToken(tokens.access_token);
};

/** Who is signed in. Needs a session to already exist. */
const fetchProfile = () => apiRequest("/api/auth/me");

const endSession = async () => {
  try {
    await apiRequest("/api/auth/logout", { method: "POST", noRetry: true });
  } finally {
    // Forget the token locally even if the call failed. They asked to be
    // logged out, so the app should stop acting as if they are not.
    setAccessToken(null);
  }
};

/** Where someone lands once they are signed in, however they got there. */
export const SIGNED_IN_HOME = "/tracks";

/**
 * The session, held in one place any component can read.
 *
 * Read a single value at a time, so a component only re-renders when the bit
 * it actually uses changes:
 *
 *   const user = useAuth((s) => s.user);
 */
export const useAuth = create((set) => ({
  /** "loading" until the app has asked the server who this is. */
  status: "loading",
  user: null,

  login: async (email, password) => {
    await startSession("/api/auth/login", { email, password });
    set({ user: await fetchProfile(), status: "authenticated" });
  },

  signUp: async (input) => {
    // Creating the account signs them in too, so this one call does both.
    await startSession("/api/auth/signup", input);
    set({ user: await fetchProfile(), status: "authenticated" });
  },

  logout: async () => {
    await endSession();
    set({ user: null, status: "anonymous" });
  },
}));

/**
 * Works out whether this browser is already signed in, once, as the app loads.
 *
 * The access token only ever lives in memory, so loading the page throws it
 * away. The refresh cookie survives though, so we ask the server whether this
 * browser still has a session and rebuild from that.
 *
 * This also covers coming back from Google: the backend attaches the refresh
 * cookie to its redirect, so by the time the app loads there is already a
 * session waiting to be picked up.
 *
 * Deliberately runs here rather than in a component. It happens once when this
 * file is first imported, so React mounting things twice in development cannot
 * make it happen twice.
 */
const restoreSession = async () => {
  const { setState } = useAuth;

  if (!(await refreshSession())) {
    setState({ status: "anonymous" });
    return;
  }

  try {
    setState({ user: await fetchProfile(), status: "authenticated" });
  } catch {
    setState({ status: "anonymous" });
  }
};

restoreSession();
