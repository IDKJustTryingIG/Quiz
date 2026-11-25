import { useEffect, useState } from "react";
import { CalendarClockIcon, LoaderIcon } from "lucide-react";
import DailyEmails from "@/components/admin/DailyEmails";
import PageHeader from "@/components/PageHeader";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useAuth } from "@/lib/auth";
import {
  SCHEDULE_ZONE,
  fetchQuizJobs,
  planQuizzes,
  scheduleToday,
} from "@/lib/admin";

/** The page reads the jobs again this often, so a running job moves on its own. */
const REFRESH_MS = 10_000;

const STATUS = {
  PENDING: { label: "Waiting", className: "bg-muted text-foreground" },
  RUNNING: {
    label: "Writing",
    className: "bg-foreground/10 text-foreground ring-1 ring-foreground/30",
  },
  DONE: { label: "Ready", className: "bg-success/15 text-success" },
  FAILED: { label: "Failed", className: "bg-destructive/15 text-destructive" },
};

const time = (iso) =>
  iso
    ? new Date(iso).toLocaleTimeString(undefined, {
        timeZone: SCHEDULE_ZONE,
        hour: "2-digit",
        minute: "2-digit",
      })
    : "";

/** What happens next, or what happened, in a few words. */
const whenText = (job) => {
  if (job.status === "DONE" || job.status === "FAILED")
    return job.finishedAt ? `at ${time(job.finishedAt)}` : "";
  if (job.status === "RUNNING") return "now";
  return job.attempts === 0 ? "next" : `retry at ${time(job.nextAttemptAt)}`;
};

const Summary = ({ jobs }) => {
  const counts = jobs.reduce(
    (acc, job) => ({ ...acc, [job.status]: (acc[job.status] ?? 0) + 1 }),
    {},
  );
  return (
    <div className="flex flex-wrap gap-2" aria-label="Jobs by status">
      {Object.entries(STATUS).map(([status, { label, className }]) => (
        <span
          key={status}
          className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium tabular-nums ${className}`}
        >
          {label} {counts[status] ?? 0}
        </span>
      ))}
    </div>
  );
};

/**
 * The quiz queue of one date, for admins: what the 06:00 and 17:00 runs
 * planned, and what the workers did with it.
 */
const AdminQuizJobs = () => {
  const user = useAuth((s) => s.user);
  const [date, setDate] = useState(scheduleToday);
  // Bumped by "Plan now" and by the timer, so the list loads again.
  const [tick, setTick] = useState(0);
  const [result, setResult] = useState({ date: null, jobs: [], error: null });
  const [planning, setPlanning] = useState(false);
  const [planMessage, setPlanMessage] = useState(null);

  useEffect(() => {
    let current = true;
    fetchQuizJobs(date)
      .then((jobs) => {
        if (current) setResult({ date, jobs, error: null });
      })
      .catch((cause) => {
        if (current) setResult({ date, jobs: [], error: cause.message });
      });
    return () => {
      current = false;
    };
  }, [date, tick]);

  useEffect(() => {
    const id = setInterval(() => {
      if (document.visibilityState === "visible") setTick((t) => t + 1);
    }, REFRESH_MS);
    return () => clearInterval(id);
  }, []);

  if (user?.role !== "ADMIN") {
    return (
      <section className="mx-auto max-w-5xl px-4 py-16">
        <p role="alert" className="text-sm text-destructive">
          Only admins can see the quiz queue.
        </p>
      </section>
    );
  }

  const plan = async () => {
    setPlanning(true);
    setPlanMessage(null);
    try {
      const r = await planQuizzes(date);
      setPlanMessage(
        `${r.needed} needed · ${r.queued} queued · ${r.alreadyReady} already ready · ${r.requeued} queued again · ${r.contests} contests`,
      );
      setTick((t) => t + 1);
    } catch (cause) {
      setPlanMessage(cause.message);
    } finally {
      setPlanning(false);
    }
  };

  const loading = result.date !== date;
  const { jobs, error } = result;

  return (
    <section className="mx-auto max-w-5xl px-4 py-16">
      <PageHeader
        title="Quiz queue"
        description={`The quizzes each day needs. The server plans them at 06:00 and 17:00 (${SCHEDULE_ZONE}), and workers write them.`}
      />

      <div className="mb-6 flex flex-wrap items-end gap-3">
        <div className="grid gap-1.5">
          <label htmlFor="queue-date" className="text-sm font-medium">
            Date
          </label>
          <Input
            id="queue-date"
            type="date"
            value={date}
            onChange={(event) =>
              event.target.value && setDate(event.target.value)
            }
            className="w-44"
          />
        </div>
        <Button onClick={plan} disabled={planning}>
          {planning ? (
            <LoaderIcon className="animate-spin" aria-hidden="true" />
          ) : (
            <CalendarClockIcon aria-hidden="true" />
          )}
          Plan now
        </Button>
        {planMessage && (
          <p role="status" className="text-sm vibrant-secondary">
            {planMessage}
          </p>
        )}
      </div>

      <div className="rounded-2xl border p-5 sm:p-6">
        <div className="mb-4">
          <Summary jobs={jobs} />
        </div>
        {loading ? (
          <p className="text-sm vibrant-secondary">Loading…</p>
        ) : error ? (
          <p role="alert" className="text-sm text-destructive">
            {error}
          </p>
        ) : jobs.length === 0 ? (
          <p className="text-sm vibrant-secondary">
            No quizzes planned for this date. Nobody is on a day of a track
            then, or the date was not planned yet.
          </p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[640px] text-left text-sm">
              <thead className="text-xs vibrant-secondary">
                <tr className="border-b">
                  <th className="py-2 pr-4 font-medium">Track</th>
                  <th className="py-2 pr-4 font-medium">Topic</th>
                  <th className="py-2 pr-4 font-medium">Status</th>
                  <th className="py-2 pr-4 text-right font-medium">Tries</th>
                  <th className="py-2 pr-4 font-medium">When</th>
                  <th className="py-2 font-medium">Last error</th>
                </tr>
              </thead>
              <tbody>
                {jobs.map((job) => {
                  const status = STATUS[job.status];
                  return (
                    <tr
                      key={job.jobId}
                      className="border-b last:border-0 align-top"
                    >
                      <td className="py-2.5 pr-4 break-words">
                        {job.trackName}
                      </td>
                      <td className="py-2.5 pr-4 break-words">
                        {job.topicName}
                      </td>
                      <td className="py-2.5 pr-4">
                        <span
                          className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${status.className}`}
                        >
                          {status.label}
                        </span>
                      </td>
                      <td className="py-2.5 pr-4 text-right tabular-nums">
                        {job.attempts}
                      </td>
                      <td className="py-2.5 pr-4 whitespace-nowrap tabular-nums vibrant-secondary">
                        {whenText(job)}
                      </td>
                      <td className="py-2.5 text-xs break-words vibrant-secondary">
                        {job.lastError}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
      <DailyEmails date={date} tick={tick} />
    </section>
  );
};
export default AdminQuizJobs;
