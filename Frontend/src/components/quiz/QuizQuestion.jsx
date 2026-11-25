import { useEffect } from "react";
import { motion } from "motion/react";
import { CheckIcon, TimerOffIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import QuizTimer from "@/components/quiz/QuizTimer";
import { spring } from "@/lib/motion";
import { LETTERS, SECONDS_PER_QUESTION } from "@/lib/quiz";
import { useReducedMotion } from "@/lib/useReducedMotion";

const optionClass = (index, answer, answered) => {
  const base =
    "flex w-full items-start gap-3 rounded-xl border px-4 py-3 text-left transition-[border-color,background-color,opacity] duration-150 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring";
  if (!answered) return `${base} hover:border-foreground/25 hover:bg-muted/50`;
  if (index === answer) return `${base} border-foreground bg-muted`;
  return `${base} opacity-50`;
};

/**
 * One question and its timer.
 *
 * The browser does not know the correct answer. The server sends it after the
 * user submits all answers, so the page only marks the chosen option here.
 *
 * answer is undefined before the user answers, null when the time ran out,
 * and the option index otherwise.
 *
 * timed shows the per-question timer. A contest has one time limit for the
 * whole attempt instead, so it passes false.
 */
const QuizQuestion = ({
  question,
  number,
  total,
  answer,
  onAnswer,
  onNext,
  timed = true,
  lastLabel = "Submit answers",
}) => {
  const reduced = useReducedMotion();
  const answered = answer !== undefined;

  // Keys 1 to 4 select an option. Enter needs no handler: after the answer,
  // focus moves to the Next button, and Enter presses it.
  useEffect(() => {
    if (answered) return;
    const onKey = (event) => {
      if (event.altKey || event.ctrlKey || event.metaKey) return;
      const index = Number(event.key) - 1;
      if (index >= 0 && index < question.options.length) onAnswer(index);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [answered, question, onAnswer]);

  return (
    <div className="grid gap-6">
      <div className="grid gap-3">
        <p className="text-sm vibrant-secondary">
          Question {number} of {total}
        </p>
        {timed && (
          <QuizTimer
            seconds={SECONDS_PER_QUESTION}
            running={!answered}
            onExpire={() => onAnswer(null)}
          />
        )}
      </div>

      <h2 className="text-lg font-medium break-words sm:text-xl">
        {question.question}
      </h2>

      <ul className="grid gap-2">
        {question.options.map((option, index) => (
          <li key={index}>
            <button
              type="button"
              disabled={answered}
              onClick={() => onAnswer(index)}
              aria-pressed={answer === index}
              className={optionClass(index, answer, answered)}
            >
              <span
                className="flex size-6 shrink-0 items-center justify-center rounded-md border text-xs font-medium"
                aria-hidden="true"
              >
                {LETTERS[index]}
              </span>
              <span className="min-w-0 break-words">{option}</span>
            </button>
          </li>
        ))}
      </ul>

      {answered && (
        <motion.div
          initial={{ opacity: 0, y: reduced ? 0 : 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={reduced ? { duration: 0.15 } : spring.default}
          className="flex flex-wrap items-center gap-3 rounded-xl bg-muted/60 p-4"
        >
          <p role="status" className="flex items-center gap-2 font-medium">
            {answer === null ? (
              <>
                <TimerOffIcon
                  className="size-4 text-destructive"
                  aria-hidden="true"
                />
                <span className="text-destructive">Time is up</span>
              </>
            ) : (
              <>
                <CheckIcon className="size-4" aria-hidden="true" />
                <span>You chose {LETTERS[answer]}</span>
              </>
            )}
          </p>
          <Button autoFocus size="lg" onClick={onNext} className="ml-auto px-4">
            {number === total ? lastLabel : "Next question"}
          </Button>
        </motion.div>
      )}
    </div>
  );
};
export default QuizQuestion;
