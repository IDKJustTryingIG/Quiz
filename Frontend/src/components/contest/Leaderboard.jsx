import { useEffect, useState } from "react";
import { TrophyIcon } from "lucide-react";
import { fetchLeaderboard, formatDuration } from "@/lib/contest";

/** The live ranking reloads this often until it is final. */
const REFRESH_MS = 15_000;

/**
 * The ranking of a contest. Live while the contest is open, then final.
 * Users on every day of the track share it.
 */
const Leaderboard = ({ sessionId }) => {
  const [board, setBoard] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    let current = true;
    let timer = null;
    const load = () =>
      fetchLeaderboard(sessionId)
        .then((next) => {
          if (!current) return;
          setBoard(next);
          setError(null);
          if (!next.isFinal) timer = setTimeout(load, REFRESH_MS);
        })
        .catch((cause) => {
          if (!current) return;
          setError(cause.message);
          timer = setTimeout(load, REFRESH_MS);
        });
    load();
    return () => {
      current = false;
      clearTimeout(timer);
    };
  }, [sessionId]);

  if (error && !board)
    return (
      <p role="alert" className="text-sm text-destructive">
        {error}
      </p>
    );
  if (!board)
    return <p className="text-sm vibrant-secondary">Loading the ranking…</p>;

  return (
    <div className="grid gap-3">
      <div className="flex flex-wrap items-center gap-2">
        <TrophyIcon className="size-4" aria-hidden="true" />
        <h2 className="font-medium">Ranking</h2>
        <span
          className={`rounded-full px-2 py-0.5 text-xs font-medium ${
            board.isFinal
              ? "bg-foreground text-background"
              : "bg-muted text-foreground"
          }`}
        >
          {board.isFinal ? "Final" : "Live"}
        </span>
      </div>
      {board.entries.length === 0 ? (
        <p className="text-sm vibrant-secondary">Nobody has started yet.</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="text-xs vibrant-secondary">
              <tr className="border-b">
                <th className="w-10 py-2 pr-3 font-medium">#</th>
                <th className="py-2 pr-3 font-medium">User</th>
                <th className="py-2 pr-3 font-medium">Day</th>
                <th className="py-2 pr-3 text-right font-medium">Score</th>
                <th className="py-2 text-right font-medium">Time</th>
              </tr>
            </thead>
            <tbody>
              {board.entries.map((entry) => (
                <tr
                  key={entry.username}
                  className={`border-b last:border-0 ${entry.you ? "bg-muted/60 font-medium" : ""}`}
                >
                  <td className="py-2 pr-3 tabular-nums">
                    {entry.rank ?? "–"}
                  </td>
                  <td className="py-2 pr-3 break-all">
                    {entry.username}
                    {entry.you && (
                      <span className="vibrant-secondary"> (you)</span>
                    )}
                  </td>
                  <td className="py-2 pr-3 tabular-nums">{entry.dayNumber}</td>
                  <td className="py-2 pr-3 text-right tabular-nums">
                    {entry.score == null ? (
                      <span className="vibrant-secondary">answering</span>
                    ) : (
                      `${entry.score}/${entry.total}`
                    )}
                  </td>
                  <td className="py-2 text-right tabular-nums">
                    {formatDuration(entry.durationMs)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};
export default Leaderboard;
