import { Navigate, Outlet, useLocation } from "react-router";
import { useAuth } from "@/lib/auth";
/**
 * Wraps the pages you have to be signed in to see.
 *
 * The waiting state is the important part. When the app starts it does not yet
 * know who you are — it has to ask the server first. Without this, a signed-in
 * person would be thrown to the login page every time they reloaded, just
 * because the answer had not come back yet.
 */
const RequireAuth = () => {
  const status = useAuth((s) => s.status);
  const location = useLocation();
  if (status === "loading") {
    return (
      <section className="grid min-h-[60svh] place-items-center px-6">
        <p role="status" className="text-sm vibrant-secondary">
          Loading…
        </p>
      </section>
    );
  }
  if (status === "anonymous") {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  return <Outlet />;
};
export default RequireAuth;
