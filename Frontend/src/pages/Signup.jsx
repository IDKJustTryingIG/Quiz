import { Link, useNavigate } from "react-router";
import AuthLayout from "@/components/AuthLayout";
import GoogleSignInButton from "@/components/GoogleSignInButton";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { SIGNED_IN_HOME, useAuth } from "@/lib/auth";
import { useAuthSubmit, useRedirectWhenSignedIn } from "@/lib/authForm";
const Signup = () => {
  const signUp = useAuth((s) => s.signUp);
  const navigate = useNavigate();
  const { pending, error, fieldErrors, run } = useAuthSubmit();
  useRedirectWhenSignedIn(SIGNED_IN_HOME);
  const handleSubmit = async (event) => {
    event.preventDefault();
    // Read the fields before anything async: after an await, React may have
    // already recycled the event.
    const data = new FormData(event.currentTarget);
    const input = {
      email: String(data.get("email")),
      username: String(data.get("username")),
      password: String(data.get("password")),
    };
    // Creating the account signs them in too, so there is nowhere else to go
    // first — straight to the tracks.
    const ok = await run(() => signUp(input));
    if (ok) navigate(SIGNED_IN_HOME, { replace: true });
  };
  return (
    <AuthLayout
      title="Create an account"
      description="Start your first track in under a minute."
      footer={
        <>
          Already have an account?{" "}
          <Link
            to="/login"
            className="font-medium text-foreground underline underline-offset-4"
          >
            Log in
          </Link>
        </>
      }
    >
      <form onSubmit={handleSubmit} className="mt-8 grid gap-5" noValidate>
        <div className="grid gap-2">
          <Label htmlFor="username">Username</Label>
          <Input
            id="username"
            name="username"
            autoComplete="username"
            placeholder="ada_l"
            minLength={3}
            maxLength={30}
            aria-invalid={Boolean(fieldErrors.username)}
            required
          />
          {fieldErrors.username ? (
            <p className="text-xs text-destructive">{fieldErrors.username}</p>
          ) : (
            <p className="text-xs vibrant-secondary">3 to 30 characters.</p>
          )}
        </div>

        <div className="grid gap-2">
          <Label htmlFor="email">Email</Label>
          <Input
            id="email"
            name="email"
            type="email"
            autoComplete="email"
            placeholder="you@example.com"
            maxLength={255}
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
            autoComplete="new-password"
            minLength={8}
            maxLength={72}
            aria-invalid={Boolean(fieldErrors.password)}
            required
          />
          {fieldErrors.password ? (
            <p className="text-xs text-destructive">{fieldErrors.password}</p>
          ) : (
            <p className="text-xs vibrant-secondary">8 to 72 characters.</p>
          )}
        </div>

        {error && (
          <p role="alert" className="text-sm text-destructive">
            {error}
          </p>
        )}

        <Button type="submit" className="mt-1 w-full" disabled={pending}>
          {pending ? "Creating account…" : "Create account"}
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
export default Signup;
