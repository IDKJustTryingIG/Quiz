import { Link } from "react-router";
import { PILL_OUTLINE, useRedirectWhenSignedIn } from "@/lib/authForm";
import { SIGNED_IN_HOME, useAuth } from "@/lib/auth";
/**
 * Where Google sends you back to.
 *
 * There is nothing to read out of the URL and nothing to send. The backend
 * already attached the refresh cookie to the redirect, so the startup check in
 * the store picks the session up on its own — exactly as it would after a
 * normal page reload. This screen only waits for that to finish.
 */
const OAuthSuccess = () => {
  const status = useAuth((s) => s.status);
  useRedirectWhenSignedIn(SIGNED_IN_HOME);
  if (status === "anonymous") {
    return (
      <section className="grid min-h-svh place-items-center px-6 text-center">
        <div>
          <h1 className="text-2xl font-medium">Sign-in did not finish</h1>
          <p className="mx-auto mt-2 max-w-[42ch] text-sm vibrant-secondary">
            Google sent you back, but no session arrived with you. This usually
            means the browser blocked the cookie. Try again.
          </p>
          <Link to="/login" className={`mt-6 inline-block ${PILL_OUTLINE}`}>
            Back to log in
          </Link>
        </div>
      </section>
    );
  }
  return (
    <section className="grid min-h-svh place-items-center px-6 text-center">
      <p role="status" className="text-sm vibrant-secondary">
        Finishing sign-in…
      </p>
    </section>
  );
};
export default OAuthSuccess;
