import { useEffect, useState } from "react";
import { Menu } from "lucide-react";
import { motion } from "motion/react";
import { Link, NavLink } from "react-router";
import GestureSheet from "@/components/GestureSheet";
import ThemeToggle from "@/components/ThemeToggle";
import UserMenu from "@/components/UserMenu";
import { Button } from "@/components/ui/button";
import { spring } from "@/lib/motion";
const links = [
  { to: "/tracks", label: "Tracks" },
  { to: "/quiz", label: "Quiz" },
  { to: "/battle", label: "Battle" },
];
const Navbar = () => {
  const [open, setOpen] = useState(false);
  const [scrolled, setScrolled] = useState(false);
  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 4);
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);
  return (
    <>
      <header className="material-chrome sticky top-0 z-30">
        <nav className="mx-auto flex h-14 max-w-5xl items-center justify-between px-4">
          <Link
            to="/"
            aria-label="Quiz home"
            className="grid size-8 place-items-center rounded-[0.6rem] border border-foreground text-base font-bold transition-transform duration-100 ease-out active:scale-[0.94] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
          >
            Q
          </Link>

          <ul className="hidden items-center gap-1 sm:flex">
            {links.map(({ to, label }) => (
              <li key={to}>
                <NavLink
                  to={to}
                  className={({ isActive }) =>
                    `relative block rounded-full px-3.5 py-1.5 text-sm font-medium transition-[color,transform] duration-100 ease-out active:scale-[0.96] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring ${
                      isActive
                        ? "text-foreground"
                        : "vibrant-secondary hover:text-foreground"
                    }`
                  }
                >
                  {({ isActive }) => (
                    <>
                      {isActive && (
                        <motion.span
                          layoutId="nav-active"
                          transition={spring.snappy}
                          className="absolute inset-0 -z-10 rounded-full bg-foreground/8"
                        />
                      )}
                      {label}
                    </>
                  )}
                </NavLink>
              </li>
            ))}
          </ul>

          <div className="flex items-center gap-1">
            <ThemeToggle />
            <UserMenu />

            <Button
              variant="ghost"
              size="icon"
              aria-label="Open menu"
              aria-expanded={open}
              onClick={() => setOpen(true)}
              className="transition-transform duration-100 ease-out active:scale-[0.94] sm:hidden"
            >
              <Menu />
            </Button>
          </div>
        </nav>

        {/* Scroll edge effect: chrome only separates itself from content once
            there is content underneath it to separate from (§12). */}
        <div
          aria-hidden
          className="pointer-events-none absolute inset-x-0 top-full h-6 bg-gradient-to-b from-foreground/8 to-transparent transition-opacity duration-300"
          style={{ opacity: scrolled ? 1 : 0 }}
        />
      </header>

      <GestureSheet open={open} onOpenChange={setOpen} label="Menu">
        <ul className="grid gap-1">
          {links.map(({ to, label }) => (
            <li key={to}>
              <NavLink
                to={to}
                onClick={() => setOpen(false)}
                className={({ isActive }) =>
                  `block rounded-2xl px-4 py-3.5 text-lg transition-[background-color,transform] duration-100 ease-out active:scale-[0.98] ${
                    isActive
                      ? "bg-foreground/8 font-medium text-foreground"
                      : "vibrant-secondary"
                  }`
                }
              >
                {label}
              </NavLink>
            </li>
          ))}
        </ul>
      </GestureSheet>
    </>
  );
};
export default Navbar;
