import { Link, useSearchParams } from "react-router";
import { PILL_OUTLINE } from "@/lib/authForm";
/**
 * The backend only ever sends a short reason code, never the real detail, so
 * that a failed sign-in cannot be used to probe for accounts. Translate the
 * codes we know about and stay vague about the rest.
 */
const REASONS = {
  account_not_provisioned:
    "Google did not return a verified email address for that account.",
  unsupported_principal: "That sign-in method is not supported.",
};
const OAuthFailure = () => {
  const [params] = useSearchParams();
  const reason = params.get("error") ?? "";
  return (
    <section className="grid min-h-svh place-items-center px-6 text-center">
      <div>
        <h1 className="text-2xl font-medium">
          Google sign-in did not complete
        </h1>
        <p className="mt-2 max-w-[42ch] text-sm vibrant-secondary">
          {REASONS[reason] ??
            "Try again, or sign in with your email and password instead."}
        </p>
        <Link to="/login" className={`mt-6 inline-block ${PILL_OUTLINE}`}>
          Back to log in
        </Link>
      </div>
    </section>
  );
};
export default OAuthFailure;
