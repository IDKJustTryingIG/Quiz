import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import { AnimatePresence, motion } from "motion/react";
import { ArrowLeftIcon, LoaderIcon, SparklesIcon } from "lucide-react";
import QuizQuestion from "@/components/quiz/QuizQuestion";
import QuizResults from "@/components/quiz/QuizResults";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/lib/auth";
import { spring } from "@/lib/motion";
import {
  checkAnswers,
  fetchQuiz,
  fetchTrackDetail,
  prepareQuiz,
} from "@/lib/tracks";
import { useReducedMotion } from "@/lib/useReducedMotion";

/**
 * The URL has the slug, but the quiz endpoint needs the track id, so the
 * track is read first. That also gives the names for the page heading.
 *
 * quiz is null when the day has no quiz yet. Opening a quiz never asks the
 * model, so a missing quiz is a normal state, not an error.
 */
const loadQuiz = async (slug, topicId) => {
  const track = await fetchTrackDetail(slug);
  const day = track.days.find((d) => String(d.topicId) === topicId);
  if (!day) throw new Error("This day is not part of the track.");
  try {
    const quiz = await fetchQuiz(track.trackId, day.topicId);
    return { track, day, quiz };
  } catch (cause) {
    if (cause.status === 404) return { track, day, quiz: null };
    // Today's contest uses this quiz. Practice opens after the contest.
    if (cause.status === 409)
      return { track, day, quiz: null, locked: cause.message };
    throw cause;
  }
};

/**
 * Runs one quiz from the first question to the score. Remount it to restart.
 *
 * The browser does not know the answers. After the last question it sends
 * all answers, and the server returns the score and the correct answers.
 */
const QuizRunner = ({ track, day, quiz, onRetry, onBack }) => {
  const reduced = useReducedMotion();
  const { questions } = quiz;
  const [index, setIndex] = useState(0);
  // answers[i] is the option index, or null when the time ran out.
  const [answers, setAnswers] = useState([]);
  // null until the user submits, then { status: "sending" | "done" | "error", ... }.
  const [submission, setSubmission] = useState(null);

  // The timer and a click can arrive together. Only the first one counts.
  const answer = useCallback(
    (choice) =>
      setAnswers((prev) => (prev.length > index ? prev : [...prev, choice])),
    [index],
  );

  const submit = async () => {
    setSubmission({ status: "sending" });
    try {
      const result = await checkAnswers(
        track.trackId,
        day.topicId,
        quiz.quizId,
        questions.map((question, i) => ({
          questionId: question.questionId,
          chosenIndex: answers[i] ?? null,
        })),
      );
      setSubmission({ status: "done", result });
    } catch (cause) {
      setSubmission({ status: "error", error: cause.message });
    }
  };

  const next = () => {
    setIndex((i) => i + 1);
    if (index === questions.length - 1) submit();
  };

  const done = index >= questions.length;

  let content;
  if (!done) {
    content = (
      <QuizQuestion
        question={questions[index]}
        number={index + 1}
        total={questions.length}
        answer={answers[index]}
        onAnswer={answer}
        onNext={next}
      />
    );
  } else if (submission?.status === "done") {
    content = (
      <QuizResults
        questions={questions}
        result={submission.result}
        onRetry={onRetry}
        onBack={onBack}
      />
    );
  } else if (submission?.status === "error") {
    content = (
      <div className="grid gap-4">
        <p role="alert" className="text-sm text-destructive">
          {submission.error}
        </p>
        <Button onClick={submit} className="justify-self-start">
          Send answers again
        </Button>
      </div>
    );
  } else {
    content = (
      <div className="grid justify-items-center gap-3 py-10 text-center">
        <LoaderIcon className="size-6 animate-spin vibrant-secondary" />
        <p className="font-medium">Checking your answers…</p>
      </div>
    );
  }

  return (
    <AnimatePresence mode="wait" initial={false}>
      <motion.div
        key={done ? `results-${submission?.status}` : index}
        initial={{ opacity: 0, x: reduced ? 0 : 16 }}
        animate={{ opacity: 1, x: 0 }}
        exit={{ opacity: 0, x: reduced ? 0 : -16 }}
        transition={reduced ? { duration: 0.15 } : spring.snappy}
      >
        {content}
      </motion.div>
    </AnimatePresence>
  );
};

/**
 * The day has no quiz yet. Only the owner can make it now. Until the daily
 * job exists, this is how a quiz gets made.
 */
const NotReady = ({ isOwner, onPrepare }) => {
  const [preparing, setPreparing] = useState(false);
  const [error, setError] = useState(null);

  const prepare = async () => {
    setPreparing(true);
    setError(null);
    try {
      await onPrepare();
    } catch (cause) {
      setError(cause.message);
      setPreparing(false);
    }
  };

  if (preparing)
    return (
      <div className="grid justify-items-center gap-3 py-10 text-center">
        <LoaderIcon className="size-6 animate-spin vibrant-secondary" />
        <p className="font-medium">Writing the quiz…</p>
        <p className="max-w-sm text-sm vibrant-secondary">
          The questions come from the files for this day. This can take up to a
          minute.
        </p>
      </div>
    );

  return (
    <div className="grid gap-4">
      <p className="font-medium">The quiz for this day is not ready yet.</p>
      <p className="text-sm vibrant-secondary">
        {isOwner
          ? "You own this track, so you can make the quiz now. Every user of the track then gets the same quiz."
          : "Come back later. The quiz is made before the day starts."}
      </p>
      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
      {isOwner && (
        <Button onClick={prepare} className="justify-self-start">
          <SparklesIcon aria-hidden="true" />
          Make the quiz now
        </Button>
      )}
    </div>
  );
};

const TopicQuiz = () => {
  const { slug, topicId } = useParams();
  const navigate = useNavigate();
  // Goes up by one for each "Retake quiz", so the quiz loads again.
  const [attempt, setAttempt] = useState(0);
  const key = `${slug}/${topicId}/${attempt}`;
  const [result, setResult] = useState({ key: null });
  const user = useAuth((s) => s.user);

  // In development StrictMode runs this effect twice, so the request is kept
  // here and the second run reuses it.
  const pending = useRef(null);

  useEffect(() => {
    if (pending.current?.key !== key)
      pending.current = { key, promise: loadQuiz(slug, topicId) };
    let current = true;
    pending.current.promise
      .then((loaded) => {
        if (current) setResult({ key, ...loaded });
      })
      .catch((cause) => {
        if (current)
          setResult({
            key,
            error:
              cause.status === 404
                ? "This track does not exist, or it is private."
                : cause.message,
          });
      });
    return () => {
      current = false;
    };
  }, [key, slug, topicId]);

  const loading = result.key !== key;
  const backTo = `/tracks/${encodeURIComponent(slug)}`;
  const isOwner = Boolean(
    result.track && user && user.userId === result.track.ownerId,
  );

  const prepare = async () => {
    await prepareQuiz(result.track.trackId, result.day.topicId);
    // Read it like any user would, so a quiz in today's contest stays locked.
    setAttempt((a) => a + 1);
  };

  let body;
  if (loading) {
    body = (
      <div className="grid justify-items-center gap-3 py-10 text-center">
        <LoaderIcon className="size-6 animate-spin vibrant-secondary" />
        <p className="font-medium">Loading your quiz…</p>
      </div>
    );
  } else if (result.error) {
    body = (
      <div className="grid gap-4">
        <p role="alert" className="text-sm text-destructive">
          {result.error}
        </p>
        <button
          type="button"
          onClick={() => setAttempt((a) => a + 1)}
          className="justify-self-start text-sm underline underline-offset-4"
        >
          Try again
        </button>
      </div>
    );
  } else if (result.locked) {
    body = (
      <div className="grid gap-3">
        <p className="font-medium">{result.locked}</p>
        <Link to="/contest" className="text-sm underline underline-offset-4">
          Go to today’s quiz
        </Link>
      </div>
    );
  } else if (!result.quiz) {
    body = <NotReady isOwner={isOwner} onPrepare={prepare} />;
  } else {
    body = (
      <QuizRunner
        key={`${key}/${result.quiz.quizId}`}
        track={result.track}
        day={result.day}
        quiz={result.quiz}
        onRetry={() => setAttempt((a) => a + 1)}
        onBack={() => navigate(backTo)}
      />
    );
  }

  return (
    <section className="mx-auto max-w-3xl px-4 py-16">
      <Link
        to={backTo}
        className="mb-4 inline-flex max-w-full items-center gap-1.5 text-sm vibrant-secondary hover:text-foreground"
      >
        <ArrowLeftIcon className="size-4 shrink-0" aria-hidden="true" />
        <span className="truncate">
          {result.track ? result.track.name : "Back to track"}
        </span>
      </Link>
      <div className="rounded-2xl border p-5 sm:p-8">
        {result.day && !loading && (
          <h1 className="mb-6 text-sm font-medium break-words vibrant-secondary">
            Day {result.day.position + 1} quiz · {result.day.topicName}
          </h1>
        )}
        {body}
      </div>
    </section>
  );
};
export default TopicQuiz;
