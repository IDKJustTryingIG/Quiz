import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router";
import { ApiRequestError } from "@/lib/api";
import { useAuth } from "@/lib/auth";
/** Shown when the request never reached the server at all. */
const OFFLINE =
  "Could not reach the server. Check that the backend is running on port 8080.";
/** Shared button styles, so the auth screens stay visually identical. */
export const PILL_SOLID =
  "rounded-full bg-foreground px-5 py-2.5 text-sm font-medium text-background transition-transform duration-100 ease-out active:scale-[0.97] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring";
export const PILL_OUTLINE =
  "rounded-full border px-5 py-2.5 text-sm font-medium transition-[transform,border-color] duration-100 ease-out hover:border-foreground/25 active:scale-[0.97] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring";
/**
 * Sends someone away once they are signed in.
 *
 * Used by the login and signup screens so an already-signed-in visitor does not
 * sit looking at a form they do not need, and by the Google landing page, which
 * is nothing but a wait for this to happen.
 */
export const useRedirectWhenSignedIn = (to) => {
  const status = useAuth((s) => s.status);
  const navigate = useNavigate();
  useEffect(() => {
    if (status === "authenticated") navigate(to, { replace: true });
  }, [status, to, navigate]);
};
/**
 * The bits every auth form needs: a busy flag while the request is out, a
 * message when it fails, and per-field messages when the server says which
 * field was wrong.
 *
 * `run` takes the request to make and hands back true if it worked, so the page
 * can decide where to go next.
 */
export const useAuthSubmit = () => {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState(null);
  const [fieldErrors, setFieldErrors] = useState({});
  const run = useCallback(
    async (
      action,
      /** Turns a server rejection into wording that suits this form. */
      describe,
    ) => {
      setPending(true);
      setError(null);
      setFieldErrors({});
      try {
        await action();
        return true;
      } catch (cause) {
        if (cause instanceof ApiRequestError) {
          setFieldErrors(cause.fieldErrors ?? {});
          // When the server blamed specific fields, those messages already sit
          // under the inputs, so a banner saying the same thing is just noise.
          setError(
            cause.fieldErrors ? null : (describe?.(cause) ?? cause.message),
          );
        } else {
          setError(OFFLINE);
        }
        return false;
      } finally {
        setPending(false);
      }
    },
    [],
  );
  return { pending, error, fieldErrors, run };
};
