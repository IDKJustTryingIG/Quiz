import { useEffect, useState } from "react";
import { LoaderIcon, MailIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { SCHEDULE_ZONE, fetchEmails, planEmails } from "@/lib/admin";

const STATUS = {
  PENDING: { label: "Waiting", className: "bg-muted text-foreground" },
  SENT: { label: "Sent", className: "bg-success/15 text-success" },
  FAILED: { label: "Failed", className: "bg-destructive/15 text-destructive" },
  SKIPPED: { label: "Skipped", className: "bg-muted vibrant-secondary" },
};

const time = (iso) =>
  iso
    ? new Date(iso).toLocaleTimeString(undefined, {
        timeZone: SCHEDULE_ZONE,
        hour: "2-digit",
        minute: "2-digit",
      })
    : "";

/**
 * The 06:00 emails of one date, for the admin page. tick goes up when the
 * page wants a fresh list.
 */
const DailyEmails = ({ date, tick }) => {
  const [result, setResult] = useState({ key: null, rows: [], error: null });
  const [planning, setPlanning] = useState(false);
  const [message, setMessage] = useState(null);
  const [reload, setReload] = useState(0);
  const key = `${date}/${tick}/${reload}`;

  useEffect(() => {
    let current = true;
    fetchEmails(date)
      .then((rows) => current && setResult({ key, rows, error: null }))
      .catch(
        (cause) =>
          current && setResult({ key, rows: [], error: cause.message }),
      );
    return () => {
      current = false;
    };
  }, [date, key]);

  const plan = async () => {
    setPlanning(true);
    setMessage(null);
    try {
      const r = await planEmails(date);
      setMessage(
        `${r.planned} planned · ${r.alreadyPlanned} already planned · ${r.optedOut} turned off. Sending within 30 seconds.`,
      );
      setReload((n) => n + 1);
    } catch (cause) {
      setMessage(cause.message);
    } finally {
      setPlanning(false);
    }
  };

  const { rows, error } = result;

  return (
    <div className="mt-10">
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <h2 className="flex items-center gap-2 text-xl font-semibold">
          <MailIcon className="size-5" aria-hidden="true" />
          Daily emails
        </h2>
        <Button variant="outline" onClick={plan} disabled={planning}>
          {planning && (
            <LoaderIcon className="animate-spin" aria-hidden="true" />
          )}
          Plan emails now
        </Button>
        {message && (
          <p role="status" className="text-sm vibrant-secondary">
            {message}
          </p>
        )}
      </div>
      <div className="rounded-2xl border p-5 sm:p-6">
        {error ? (
          <p role="alert" className="text-sm text-destructive">
            {error}
          </p>
        ) : rows.length === 0 ? (
          <p className="text-sm vibrant-secondary">
            No emails for this date. The server plans them at 06:00 for every
            user who has a day of their track.
          </p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[600px] text-left text-sm">
              <thead className="text-xs vibrant-secondary">
                <tr className="border-b">
                  <th className="py-2 pr-4 font-medium">User</th>
                  <th className="py-2 pr-4 font-medium">Track</th>
                  <th className="py-2 pr-4 font-medium">Status</th>
                  <th className="py-2 pr-4 text-right font-medium">Tries</th>
                  <th className="py-2 pr-4 font-medium">Sent</th>
                  <th className="py-2 font-medium">Last error</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => (
                  <tr
                    key={row.deliveryId}
                    className="border-b align-top last:border-0"
                  >
                    <td className="py-2.5 pr-4 break-all">{row.username}</td>
                    <td className="py-2.5 pr-4 break-words">{row.trackName}</td>
                    <td className="py-2.5 pr-4">
                      <span
                        className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${STATUS[row.status].className}`}
                      >
                        {STATUS[row.status].label}
                      </span>
                    </td>
                    <td className="py-2.5 pr-4 text-right tabular-nums">
                      {row.attempts}
                    </td>
                    <td className="py-2.5 pr-4 whitespace-nowrap tabular-nums vibrant-secondary">
                      {time(row.sentAt)}
                    </td>
                    <td className="py-2.5 text-xs break-words vibrant-secondary">
                      {row.lastError}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
};
export default DailyEmails;
