import { Link } from "react-router";
import ThemeToggle from "@/components/ThemeToggle";
const surfaces = [
  { name: "Tracks", detail: "Problem sets that build on each other." },
  { name: "Quiz", detail: "Timed questions that check what stuck." },
  { name: "Battle", detail: "Head-to-head rounds against another coder." },
];
const AuthLayout = ({ title, description, children, footer }) => {
  return (
    <div className="grid min-h-svh lg:grid-cols-2">
      <aside className="hidden flex-col justify-center gap-12 border-r px-10 py-12 lg:flex xl:px-16">
        <Link
          to="/"
          aria-label="Quiz home"
          className="grid size-14 place-items-center rounded-xl border border-foreground text-2xl font-bold tracking-tight focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
        >
          Q
        </Link>

        <h1 className="max-w-[16ch] text-4xl leading-[1.15] font-medium tracking-tight text-balance xl:text-5xl">
          Train on tracks. Settle it in battle.
        </h1>

        <dl className="grid max-w-md gap-3 text-sm">
          {surfaces.map(({ name, detail }) => (
            <div key={name} className="grid grid-cols-[5.5rem_1fr] gap-4">
              <dt className="font-medium">{name}</dt>
              <dd className="text-muted-foreground">{detail}</dd>
            </div>
          ))}
        </dl>
      </aside>

      <main className="flex flex-col">
        <div className="flex justify-end p-4">
          <ThemeToggle />
        </div>

        <div className="flex flex-1 items-center justify-center px-6 pb-20">
          <div className="w-full max-w-sm">
            <Link
              to="/"
              aria-label="Quiz home"
              className="mb-10 grid size-10 place-items-center rounded-lg border border-foreground font-bold tracking-tight focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring lg:hidden"
            >
              Q
            </Link>

            <h2 className="text-2xl font-medium tracking-tight">{title}</h2>
            <p className="mt-2 text-sm text-muted-foreground">{description}</p>

            {children}

            <p className="mt-8 text-sm text-muted-foreground">{footer}</p>
          </div>
        </div>
      </main>
    </div>
  );
};
export default AuthLayout;
