import PageHeader from "@/components/PageHeader";
import ThemeToggle from "@/components/ThemeToggle";
import { useAuth } from "@/lib/auth";
import { useTheme } from "@/lib/theme";
const Account = () => {
  const user = useAuth((s) => s.user);
  const theme = useTheme((s) => s.theme);
  return (
    <section className="mx-auto max-w-5xl px-4 py-16">
      <PageHeader
        title="My account"
        description="Sign-in details and how the app looks on this device."
      />

      <dl className="grid overflow-hidden rounded-2xl border">
        <div className="flex flex-wrap items-center justify-between gap-x-6 gap-y-1 border-b px-5 py-4">
          <dt className="font-medium">Email</dt>
          <dd className="text-sm vibrant-secondary">
            {user?.email ?? "Not signed in"}
          </dd>
        </div>
        <div className="flex flex-wrap items-center justify-between gap-x-6 gap-y-1 px-5 py-4">
          <dt className="font-medium">
            Appearance
            <span className="mt-0.5 block text-sm font-normal vibrant-secondary">
              Currently {theme}. Follows your system until you choose.
            </span>
          </dt>
          <dd>
            <ThemeToggle />
          </dd>
        </div>
      </dl>
    </section>
  );
};
export default Account;
