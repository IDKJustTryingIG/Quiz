import { useState } from "react";
import { Link, useSearchParams } from "react-router";
import { MailXIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { apiRequest } from "@/lib/api";

/**
 * The page behind "Stop daily emails". It needs no sign-in: the token in
 * the link is the proof.
 *
 * It asks for a click instead of acting on load, because some mail filters
 * open every link in an email to check it.
 */
const Unsubscribe = () => {
  const [params] = useSearchParams();
  const token = params.get("token");
  const [status, setStatus] = useState("idle");
  const [error, setError] = useState(null);

  const stop = async () => {
    setStatus("sending");
    setError(null);
    try {
      await apiRequest(
        `/api/email/unsubscribe?${new URLSearchParams({ token })}`,
        { method: "POST", noRetry: true },
      );
      setStatus("done");
    } catch (cause) {
      setError(cause.message);
      setStatus("idle");
    }
  };

  let body;
  if (!token) {
    body = (
      <p role="alert" className="text-sm text-destructive">
        This link is not complete. Open it from the email again.
      </p>
    );
  } else if (status === "done") {
    body = (
      <div className="grid gap-2">
        <p className="font-medium">You will get no more daily emails.</p>
        <p className="text-sm vibrant-secondary">
          The daily quiz still opens at 6:00 PM. To get the emails again, turn
          them on from the page of your track.
        </p>
        <Link to="/tracks" className="text-sm underline underline-offset-4">
          Go to tracks
        </Link>
      </div>
    );
  } else {
    body = (
      <div className="grid gap-4">
        <p className="text-sm vibrant-secondary">
          You get an email each morning with the topic of the day. Your
          enrollment and the daily quiz stay as they are.
        </p>
        {error && (
          <p role="alert" className="text-sm text-destructive">
            {error}
          </p>
        )}
        <Button
          onClick={stop}
          disabled={status === "sending"}
          className="justify-self-start"
        >
          {status === "sending" ? "Stopping…" : "Stop daily emails"}
        </Button>
      </div>
    );
  }

  return (
    <section className="mx-auto max-w-lg px-4 py-16">
      <div className="grid gap-4 rounded-2xl border p-6">
        <div className="flex items-center gap-3">
          <MailXIcon className="size-5" aria-hidden="true" />
          <h1 className="text-xl font-semibold">Daily emails</h1>
        </div>
        {body}
      </div>
    </section>
  );
};
export default Unsubscribe;
