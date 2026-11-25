import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useNavigate } from "react-router";
import { AnimatePresence, motion } from "motion/react";
import { LoaderIcon, PlayIcon, TimerIcon } from "lucide-react";
import Leaderboard from "@/components/contest/Leaderboard";
import QuizQuestion from "@/components/quiz/QuizQuestion";
import QuizResults from "@/components/quiz/QuizResults";
import { Button } from "@/components/ui/button";
import {
  fetchAttempt,
  fetchContestToday,
  fetchResult,
  formatClock,
  formatSeconds,
  saveAnswer,
  startAttempt,
  submitAttempt,
  useServerNow,
} from "@/lib/contest";
import { spring } from "@/lib/motion";
import { useReducedMotion } from "@/lib/useReducedMotion";

const Waiting = ({ children }) => (
  <div className="grid justify-items-center gap-3 py-10 text-center">
    <LoaderIcon className="size-6 animate-spin vibrant-secondary" />
    <p className="font-medium">{children}</p>
  </div>
);

/** Calls onReached once, when the server clock passes target. */
const useWhenReached = (now, target, onReached) => {
  const fired = useRef(false);
  useEffect(() => {
    if (!fired.current && now >= Date.parse(target)) {
      fired.current = true;
      onReached();
    }
  }, [now, target, onReached]);
};

const Rules = ({ today }) => (
  <ul className="grid gap-1 text-sm vibrant-secondary">
    <li>
      Start between {formatClock(today.opensAt)} and{" "}
      {formatClock(today.startDeadline)}.
    </li>
    <li>
      You have 10 minutes, and one try. Your first answer to a question is
      final.
    </li>
    <li>Everyone in the track shares one ranking, whatever their day.</li>
    <li>The correct answers appear at {formatClock(today.closesAt)}.</li>
  </ul>
);

const Upcoming = ({ today, onOpened }) => {
  const now = useServerNow(today.serverTime);
  useWhenReached(now, today.opensAt, onOpened);
  const left = (Date.parse(today.opensAt) - now) / 1000;

  return (
    <div className="grid gap-6">
      <div className="grid justify-items-center gap-2 py-6 text-center">
        <p className="text-sm vibrant-secondary">The quiz opens in</p>
        <p
          className="text-5xl font-semibold tabular-nums sm:text-6xl"
          aria-live="off"
        >
          {formatSeconds(left)}
        </p>
        <p className="text-sm vibrant-secondary">
          at {formatClock(today.opensAt)}
        </p>
      </div>
      <Rules today={today} />
      {!today.quizReady && (
        <p className="text-sm vibrant-secondary">
          Your quiz is still being written. It will be ready before the start.
        </p>
      )}
    </div>
  );
};

const StartPanel = ({ today, onStarted, onMissed }) => {
  const now = useServerNow(today.serverTime);
  useWhenReached(now, today.startDeadline, onMissed);
  const [starting, setStarting] = useState(false);
  const [error, setError] = useState(null);

  const start = async () => {
    setStarting(true);
    setError(null);
    try {
      onStarted(await startAttempt(today.sessionId));
    } catch (cause) {
      setError(cause.message);
      setStarting(false);
    }
  };

  return (
    <div className="grid gap-6">
      <div className="grid justify-items-center gap-3 py-6 text-center">
        <p className="text-sm vibrant-secondary">
          The quiz is open. Start in the next{" "}
          <span className="font-medium text-foreground tabular-nums">
            {formatSeconds((Date.parse(today.startDeadline) - now) / 1000)}
          </span>
          .
        </p>
        <Button size="lg" onClick={start} disabled={starting} className="px-6">
          <PlayIcon aria-hidden="true" />
          {starting ? "Starting…" : "Start the quiz"}
        </Button>
        {error && (
          <p role="alert" className="text-sm text-destructive">
            {error}
          </p>
        )}
      </div>
      <Rules today={today} />
    </div>
  );
};

/**
 * Runs one contest attempt. Each answer is saved on the server at once, so
 * a reload resumes where the user was. At the deadline the attempt ends by
 * itself, and the saved answers count.
 */
const ContestRunner = ({ attempt, onFinished }) => {
  const reduced = useReducedMotion();
  const now = useServerNow(attempt.serverTime);
  const { questions } = attempt;
  const [answers, setAnswers] = useState(() => {
    const saved = new Map(
      attempt.answers.map((a) => [a.questionId, a.chosenIndex]),
    );
    return questions.map((q) => saved.get(q.questionId));
  });
  const [index, setIndex] = useState(() => {
    const first = answers.findIndex((a) => a === undefined);
    return first === -1 ? questions.length : first;
  });
  const [error, setError] = useState(null);
  const finishing = useRef(false);

  const finish = useCallback(async () => {
    if (finishing.current) return;
    finishing.current = true;
    try {
      onFinished(await submitAttempt(attempt.attemptId));
    } catch (cause) {
      finishing.current = false;
      setError(cause.message);
    }
  }, [attempt.attemptId, onFinished]);

  useWhenReached(now, attempt.deadlineAt, finish);

  const answer = useCallback(
    (choice) => {
      if (choice === null) return;
      setAnswers((prev) => {
        if (prev[index] !== undefined) return prev;
        const next = [...prev];
        next[index] = choice;
        return next;
      });
      saveAnswer(attempt.attemptId, questions[index].questionId, choice).catch(
        (cause) => setError(cause.message),
      );
    },
    [attempt.attemptId, index, questions],
  );

  const next = () => {
    if (index === questions.length - 1) finish();
    else setIndex((i) => i + 1);
  };

  const total = Date.parse(attempt.deadlineAt) - Date.parse(attempt.startedAt);
  const leftMs = Math.max(0, Date.parse(attempt.deadlineAt) - now);
  const low = leftMs < 60_000;

  return (
    <div className="grid gap-6">
      <div className="grid gap-2">
        <p
          className={`flex items-center gap-2 text-sm font-medium tabular-nums ${low ? "text-destructive" : ""}`}
        >
          <TimerIcon className="size-4" aria-hidden="true" />
          {formatSeconds(leftMs / 1000)} left
        </p>
        <div
          className="h-1.5 overflow-hidden rounded-full bg-muted"
          aria-hidden="true"
        >
          <div
            className={`h-full rounded-full transition-[width] duration-1000 ease-linear ${low ? "bg-destructive" : "bg-foreground"}`}
            style={{ width: `${(leftMs / total) * 100}%` }}
          />
        </div>
      </div>

      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}

      {index >= questions.length ? (
        <div className="grid justify-items-center gap-3 py-6 text-center">
          <p className="font-medium">You answered every question.</p>
          <Button size="lg" onClick={finish}>
            Finish
          </Button>
        </div>
      ) : (
        <AnimatePresence mode="wait" initial={false}>
          <motion.div
            key={index}
            initial={{ opacity: 0, x: reduced ? 0 : 16 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: reduced ? 0 : -16 }}
            transition={reduced ? { duration: 0.15 } : spring.snappy}
          >
            <QuizQuestion
              question={questions[index]}
              number={index + 1}
              total={questions.length}
              answer={answers[index]}
              onAnswer={answer}
              onNext={next}
              timed={false}
              lastLabel="Finish"
            />
          </motion.div>
        </AnimatePresence>
      )}
    </div>
  );
};

/** Loads the attempt the user started, then runs it. */
const ResumeAttempt = ({ attemptId, onFinished }) => {
  const [state, setState] = useState({ attempt: null, error: null });
  useEffect(() => {
    let current = true;
    fetchAttempt(attemptId)
      .then((attempt) => current && setState({ attempt, error: null }))
      .catch(
        (cause) => current && setState({ attempt: null, error: cause.message }),
      );
    return () => {
      current = false;
    };
  }, [attemptId]);

  if (state.error)
    return (
      <p role="alert" className="text-sm text-destructive">
        {state.error}
      </p>
    );
  if (!state.attempt) return <Waiting>Loading your quiz…</Waiting>;
  return <ContestRunner attempt={state.attempt} onFinished={onFinished} />;
};

const Score = ({ attempt }) => (
  <div className="grid justify-items-center gap-1 py-4 text-center">
    <p className="text-sm vibrant-secondary">Your score</p>
    <p className="text-5xl font-semibold tabular-nums">
      {attempt.score}
      <span className="text-2xl vibrant-secondary"> / {attempt.total}</span>
    </p>
  </div>
);

/** After the close: the review with the correct answers. */
const Review = ({ attemptId, onBack }) => {
  const [state, setState] = useState(null);
  useEffect(() => {
    let current = true;
    Promise.all([fetchAttempt(attemptId), fetchResult(attemptId)])
      .then(([attempt, result]) => current && setState({ attempt, result }))
      .catch((cause) => current && setState({ error: cause.message }));
    return () => {
      current = false;
    };
  }, [attemptId]);

  if (!state) return <Waiting>Loading your answers…</Waiting>;
  if (state.error)
    return (
      <p role="alert" className="text-sm text-destructive">
        {state.error}
      </p>
    );
  return (
    <QuizResults
      questions={state.attempt.questions}
      result={state.result}
      onBack={onBack}
    />
  );
};

/**
 * Today's 18:00 contest: a countdown, the quiz, the user's score, and the
 * ranking that everyone in the track shares.
 */
const Contest = () => {
  const navigate = useNavigate();
  const [reload, setReload] = useState(0);
  const [loaded, setLoaded] = useState({
    reload: -1,
    today: null,
    error: null,
  });
  // The attempt just started on this page, so it runs without a second request.
  const [started, setStarted] = useState(null);

  useEffect(() => {
    let current = true;
    fetchContestToday()
      .then((today) => current && setLoaded({ reload, today, error: null }))
      .catch(
        (cause) =>
          current && setLoaded({ reload, today: null, error: cause.message }),
      );
    return () => {
      current = false;
    };
  }, [reload]);

  const refresh = useCallback(() => {
    setStarted(null);
    setReload((r) => r + 1);
  }, []);

  const { today, error } = loaded;
  let heading = null;
  let body;

  if (loaded.reload !== reload && !today) {
    body = <Waiting>Loading today’s quiz…</Waiting>;
  } else if (error) {
    body = (
      <p role="alert" className="text-sm text-destructive">
        {error}
      </p>
    );
  } else if (today.state === "NONE") {
    body = (
      <div className="grid gap-3">
        <p className="font-medium">You have no quiz today.</p>
        <p className="text-sm vibrant-secondary">
          Enroll in a track to get a topic and a quiz every day.
        </p>
        <Link to="/tracks" className="text-sm underline underline-offset-4">
          Find a track
        </Link>
      </div>
    );
  } else {
    heading = (
      <div className="mb-6 grid gap-1">
        <p className="text-sm vibrant-secondary">
          <Link
            to={`/tracks/${encodeURIComponent(today.trackSlug)}`}
            className="underline-offset-4 hover:underline"
          >
            {today.trackName}
          </Link>{" "}
          · Day {today.dayNumber} of {today.totalDays}
        </p>
        <h1 className="text-2xl font-semibold break-words">
          {today.topicName}
        </h1>
      </div>
    );
    const attempt = today.attempt;
    const finished = attempt && attempt.status !== "IN_PROGRESS";

    if (today.state === "NOT_PLANNED") {
      body = (
        <p className="text-sm vibrant-secondary">
          Today’s quiz is being set up. Check back soon.
        </p>
      );
    } else if (today.state === "UPCOMING") {
      body = <Upcoming today={today} onOpened={refresh} />;
    } else if (today.state === "OPEN" && started) {
      body = <ContestRunner attempt={started} onFinished={refresh} />;
    } else if (today.state === "OPEN" && attempt && !finished) {
      body = (
        <ResumeAttempt attemptId={attempt.attemptId} onFinished={refresh} />
      );
    } else if (today.state === "OPEN" && today.canStart) {
      body = (
        <StartPanel today={today} onStarted={setStarted} onMissed={refresh} />
      );
    } else if (today.state === "OPEN" && !attempt) {
      body = (
        <div className="grid gap-6">
          {today.quizReady ? (
            <p className="text-sm">
              The time to start was {formatClock(today.startDeadline)}. You can
              follow the ranking.
            </p>
          ) : (
            <div className="grid gap-2">
              <p className="text-sm">Your quiz is not ready yet.</p>
              <Button
                variant="outline"
                onClick={refresh}
                className="justify-self-start"
              >
                Check again
              </Button>
            </div>
          )}
          <Leaderboard sessionId={today.sessionId} />
        </div>
      );
    } else if (today.state === "OPEN") {
      body = (
        <div className="grid gap-6">
          <Score attempt={attempt} />
          <p className="text-center text-sm vibrant-secondary">
            The correct answers appear at {formatClock(today.closesAt)}, when
            the quiz closes for everyone.
          </p>
          <Leaderboard sessionId={today.sessionId} />
        </div>
      );
    } else {
      body = (
        <div className="grid gap-8">
          <Leaderboard sessionId={today.sessionId} />
          {attempt ? (
            <Review
              attemptId={attempt.attemptId}
              onBack={() =>
                navigate(`/tracks/${encodeURIComponent(today.trackSlug)}`)
              }
            />
          ) : (
            <p className="text-sm vibrant-secondary">
              You did not take today’s quiz.
            </p>
          )}
        </div>
      );
    }
  }

  return (
    <section className="mx-auto max-w-3xl px-4 py-16">
      <div className="rounded-2xl border p-5 sm:p-8">
        {heading}
        {body}
      </div>
    </section>
  );
};
export default Contest;
