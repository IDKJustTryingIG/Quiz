import { CalendarDaysIcon, FlagIcon } from "lucide-react";
import { Link } from "react-router";
import { formatDay, useCurrentEnrollment } from "@/lib/enrollment";

/** Above this many days, one segment per day gets too thin to read. */
const MAX_SEGMENTS = 31;

/**
 * Where the user is in the track they follow. Shows nothing if they follow
 * no track.
 */
const TodayCard = () => {
  const { enrollment } = useCurrentEnrollment();
  if (!enrollment) return null;

  const { progress, dayNumber, totalDays, trackName, trackSlug, today } =
    enrollment;

  let eyebrow, title, detail;
  if (progress === "IN_PROGRESS") {
    eyebrow = `Today · Day ${dayNumber} of ${totalDays} · Quiz at 6:00 PM`;
    title = today.topicName;
    detail = trackName;
  } else if (progress === "NOT_STARTED") {
    eyebrow = `Day 1 starts ${formatDay(enrollment.startDate)}`;
    title = trackName;
    detail = `${totalDays} ${totalDays === 1 ? "day" : "days"}, one topic each day`;
  } else {
    eyebrow = "Finished";
    title = trackName;
    detail = `You finished all ${totalDays} days. Pick your next track.`;
  }

  // Days done so far, for the bar: none before Day 1, all after the last day.
  const done = Math.min(Math.max(dayNumber - 1, 0), totalDays);
  const finished = progress === "FINISHED";

  return (
    <Link
      to={
        finished
          ? "/tracks"
          : progress === "IN_PROGRESS"
            ? "/contest"
            : `/tracks/${encodeURIComponent(trackSlug)}`
      }
      className="group mb-6 flex items-start gap-4 rounded-2xl border p-5 transition-[transform,border-color] duration-100 ease-out hover:border-foreground/25 active:scale-[0.985] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring sm:gap-6"
    >
      <span className="mt-0.5 grid size-9 shrink-0 place-items-center rounded-xl border [&>svg]:size-4">
        {finished ? <FlagIcon /> : <CalendarDaysIcon />}
      </span>
      <span className="min-w-0 flex-1">
        <span className="block text-xs vibrant-secondary">{eyebrow}</span>
        <span className="mt-1 block truncate text-lg font-medium">{title}</span>
        <span className="block truncate text-sm vibrant-secondary">
          {detail}
        </span>
        {totalDays <= MAX_SEGMENTS ? (
          <span
            className="mt-3 flex gap-1"
            role="img"
            aria-label={`${done} of ${totalDays} days done`}
          >
            {Array.from({ length: totalDays }, (_, i) => (
              <span
                key={i}
                className={`h-1.5 min-w-0 flex-1 rounded-full ${
                  i < done
                    ? "bg-foreground/70"
                    : i === dayNumber - 1 && !finished
                      ? "bg-foreground"
                      : "bg-foreground/10"
                }`}
              />
            ))}
          </span>
        ) : (
          <span
            className="mt-3 block h-1.5 overflow-hidden rounded-full bg-foreground/10"
            role="img"
            aria-label={`${done} of ${totalDays} days done`}
          >
            <span
              className="block h-full rounded-full bg-foreground/70"
              style={{ width: `${(done / totalDays) * 100}%` }}
            />
          </span>
        )}
      </span>
    </Link>
  );
};
export default TodayCard;
