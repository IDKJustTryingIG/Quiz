import { Link, useLocation, useNavigate } from "react-router";
import AuthLayout from "@/components/AuthLayout";
import GoogleSignInButton from "@/components/GoogleSignInButton";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { SIGNED_IN_HOME, useAuth } from "@/lib/auth";
import { useAuthSubmit, useRedirectWhenSignedIn } from "@/lib/authForm";
const Login = () => {
  const login = useAuth((s) => s.login);
  const navigate = useNavigate();
  const location = useLocation();
  const { pending, error, fieldErrors, run } = useAuthSubmit();
  // If the guard turned someone away from a page, send them back to it once
  // they are in. Otherwise everyone lands in the same place.
  const from = location.state?.from ?? SIGNED_IN_HOME;
  useRedirectWhenSignedIn(from);
  const handleSubmit = async (event) => {
    event.preventDefault();
    // Read the fields before anything async: after an await, React may have
    // already recycled the event.
    const data = new FormData(event.currentTarget);
    const email = String(data.get("email"));
    const password = String(data.get("password"));
    const ok = await run(
      () => login(email, password),
      // The server keeps its wording vague on purpose, so that it never reveals
      // whether an account with that email exists. Say something useful here.
      (cause) =>
        cause.status === 401
          ? "That email and password do not match an account."
          : cause.message,
    );
    if (ok) navigate(from, { replace: true });
  };
  return (
    <AuthLayout
      title="Log in"
      description="Pick up where your last track left off."
      footer={
        <>
          New here?{" "}
          <Link
            to="/signup"
            className="font-medium text-foreground underline underline-offset-4"
          >
            Create an account
          </Link>
        </>
      }
    >
      <form onSubmit={handleSubmit} className="mt-8 grid gap-5" noValidate>
        <div className="grid gap-2">
          <Label htmlFor="email">Email</Label>
          <Input
            id="email"
            name="email"
            type="email"
            autoComplete="email"
            placeholder="you@example.com"
            aria-invalid={Boolean(fieldErrors.email)}
            required
          />
          {fieldErrors.email && (
            <p className="text-xs text-destructive">{fieldErrors.email}</p>
          )}
        </div>

        <div className="grid gap-2">
          <Label htmlFor="password">Password</Label>
          <Input
            id="password"
            name="password"
            type="password"
            autoComplete="current-password"
            aria-invalid={Boolean(fieldErrors.password)}
            required
          />
          {fieldErrors.password && (
            <p className="text-xs text-destructive">{fieldErrors.password}</p>
          )}
        </div>

        {error && (
          <p role="alert" className="text-sm text-destructive">
            {error}
          </p>
        )}

        <Button type="submit" className="mt-1 w-full" disabled={pending}>
          {pending ? "Logging in…" : "Log in"}
        </Button>

        <div className="flex items-center gap-3 text-xs vibrant-secondary">
          <span className="h-px flex-1 bg-border" />
          or
          <span className="h-px flex-1 bg-border" />
        </div>

        <GoogleSignInButton />
      </form>
    </AuthLayout>
  );
};
export default Login;
