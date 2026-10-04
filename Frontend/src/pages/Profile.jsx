import PageHeader from "@/components/PageHeader";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { useAuth } from "@/lib/auth";
const stats = [
  { label: "Problems solved", value: "34" },
  { label: "Quiz average", value: "7.6 of 10" },
  { label: "Battles won", value: "12" },
  { label: "Current streak", value: "5 days" },
];
const Profile = () => {
  const user = useAuth((s) => s.user);
  return (
    <section className="mx-auto max-w-5xl px-4 py-16">
      <PageHeader
        title="Profile"
        description="What you have solved so far, and how it is going."
      />

      <div className="flex items-center gap-4">
        <Avatar size="lg">
          {user?.profilePicture && (
            <AvatarImage src={user.profilePicture} alt="" />
          )}
          <AvatarFallback>
            {user ? user.username[0].toUpperCase() : "?"}
          </AvatarFallback>
        </Avatar>
        <div className="min-w-0">
          <p className="text-lg font-medium">
            {user?.username ?? "Not signed in"}
          </p>
          <p className="truncate text-sm vibrant-secondary">
            {user?.email ?? "Log in to see your progress."}
          </p>
        </div>
      </div>

      <dl className="mt-10 grid gap-3 sm:grid-cols-2">
        {stats.map(({ label, value }) => (
          <div key={label} className="rounded-2xl border p-5">
            <dt className="text-sm vibrant-secondary">{label}</dt>
            <dd className="mt-1 text-2xl font-medium tabular-nums">{value}</dd>
          </div>
        ))}
      </dl>
    </section>
  );
};
export default Profile;
