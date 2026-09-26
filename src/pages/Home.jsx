import { Layers, Swords, Timer } from "lucide-react";
import { Link } from "react-router";
import SurfaceCard from "@/components/SurfaceCard";
import { useAuth } from "@/lib/auth";
const surfaces = [
  {
    to: "/tracks",
    name: "Tracks",
    detail: "Problem sets that build on each other, in order.",
    meta: "6 tracks · arrays through graphs",
    icon: <Layers />,
  },
  {
    to: "/quiz",
    name: "Quiz",
    detail: "Timed questions that check what actually stuck.",
    meta: "10 questions · about 6 minutes",
    icon: <Timer />,
  },
  {
    to: "/battle",
    name: "Battle",
    detail: "Head-to-head rounds against another coder.",
    meta: "Ranked · first correct answer wins",
    icon: <Swords />,
  },
];
const Home = () => {
  const user = useAuth((s) => s.user);
  return (
    <section className="mx-auto max-w-5xl px-4 py-16 sm:py-20">
      <h1 className="max-w-[14ch] text-5xl font-semibold text-balance sm:text-6xl">
        Pick your next problem.
      </h1>
      <p className="mt-5 max-w-[46ch] text-lg vibrant-secondary">
        {user
          ? `Back for more, ${user.username}. Your last track is still open.`
          : "Practice in order, check yourself against the clock, then settle it head to head."}
      </p>

      {!user && (
        <div className="mt-8 flex flex-wrap items-center gap-3">
          <Link
            to="/signup"
            className="rounded-full bg-foreground px-5 py-2.5 text-sm font-medium text-background transition-transform duration-100 ease-out active:scale-[0.97] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
          >
            Create an account
          </Link>
          <Link
            to="/login"
            className="rounded-full border px-5 py-2.5 text-sm font-medium transition-[transform,border-color] duration-100 ease-out hover:border-foreground/25 active:scale-[0.97] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
          >
            Log in
          </Link>
        </div>
      )}

      <div className="mt-16 grid gap-3 sm:grid-cols-3">
        {surfaces.map((surface) => (
          <SurfaceCard key={surface.to} {...surface} />
        ))}
      </div>
    </section>
  );
};
export default Home;
