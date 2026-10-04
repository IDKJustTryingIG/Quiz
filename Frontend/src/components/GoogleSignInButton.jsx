import { apiUrl } from "@/lib/api";
/**
 * Hands the browser over to the backend to start Google sign-in.
 *
 * This is a plain link, not a fetch, and that is deliberate. The browser itself
 * has to visit Google so Google can recognise you and show the approval screen.
 * A fetch could not follow that redirect, and would get blocked on the way.
 *
 * When it is all done the backend sends the browser back to /oauth2/success
 * with the session already attached.
 */
const GoogleSignInButton = () => (
  <a
    href={apiUrl("/oauth2/authorization/google")}
    className="flex w-full items-center justify-center gap-2.5 rounded-full border px-5 py-2.5 text-sm font-medium transition-[transform,border-color] duration-100 ease-out hover:border-foreground/25 active:scale-[0.97] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
  >
    <svg aria-hidden viewBox="0 0 18 18" className="size-4">
      <path
        fill="#4285F4"
        d="M17.64 9.2c0-.64-.06-1.25-.16-1.84H9v3.48h4.84a4.14 4.14 0 0 1-1.8 2.72v2.26h2.92c1.71-1.57 2.68-3.89 2.68-6.62Z"
      />
      <path
        fill="#34A853"
        d="M9 18c2.43 0 4.47-.8 5.96-2.18l-2.92-2.26c-.81.54-1.84.86-3.04.86-2.34 0-4.32-1.58-5.03-3.7H.96v2.33A9 9 0 0 0 9 18Z"
      />
      <path
        fill="#FBBC05"
        d="M3.97 10.72a5.4 5.4 0 0 1 0-3.44V4.95H.96a9 9 0 0 0 0 8.1l3.01-2.33Z"
      />
      <path
        fill="#EA4335"
        d="M9 3.58c1.32 0 2.5.45 3.44 1.35l2.58-2.59C13.46.89 11.43 0 9 0A9 9 0 0 0 .96 4.95l3.01 2.33C4.68 5.16 6.66 3.58 9 3.58Z"
      />
    </svg>
    Continue with Google
  </a>
);
export default GoogleSignInButton;
