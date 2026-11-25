import { CheckIcon, XIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { LETTERS } from "@/lib/quiz";

const remark = (score, total) => {
  if (score === total) return "A perfect score.";
  if (score >= total * 0.8) return "Great work.";
  if (score >= total * 0.5)
    return "Good effort. Review the questions you missed.";
  return "Read the files for this day again, then retake the quiz.";
};

const answerText = (question, index) =>
  index === null
    ? "No answer. The time ran out."
    : `${LETTERS[index]}. ${question.options[index]}`;

/**
 * The score the server gave (1 mark for each correct answer) and a review of
 * every question. result.results is in question order. Without onRetry
 * (a contest allows one attempt) there is no Retake button.
 */
const QuizResults = ({ questions, result, onRetry, onBack }) => (
  <div className="grid gap-8">
    <div className="grid justify-items-center gap-2 text-center">
      <p className="text-sm vibrant-secondary">Your score</p>
      <p className="text-5xl font-semibold tabular-nums">
        {result.score}
        <span className="text-2xl vibrant-secondary"> / {result.total}</span>
      </p>
      <p className="text-sm vibrant-secondary">
        {remark(result.score, result.total)}
      </p>
      <div className="mt-4 flex flex-wrap justify-center gap-2">
        {onRetry && (
          <Button size="lg" onClick={onRetry} className="px-4">
            Retake quiz
          </Button>
        )}
        <Button size="lg" variant="outline" onClick={onBack} className="px-4">
          Back to track
        </Button>
      </div>
    </div>

    <div>
      <h2 className="mb-3 text-sm font-medium vibrant-secondary">Review</h2>
      <ol className="grid gap-3">
        {questions.map((question, i) => {
          const checked = result.results[i];
          return (
            <li
              key={question.questionId}
              className="grid gap-2 rounded-xl border p-4"
            >
              <p className="flex items-start gap-2 font-medium">
                {checked.correct ? (
                  <CheckIcon
                    className="mt-1 size-4 shrink-0 text-success"
                    aria-label="Correct"
                  />
                ) : (
                  <XIcon
                    className="mt-1 size-4 shrink-0 text-destructive"
                    aria-label="Wrong"
                  />
                )}
                <span className="min-w-0 break-words">
                  {i + 1}. {question.question}
                </span>
              </p>
              <dl className="grid gap-1 pl-6 text-sm">
                {!checked.correct && (
                  <div>
                    <dt className="inline vibrant-secondary">Your answer: </dt>
                    <dd className="inline break-words text-destructive">
                      {answerText(question, checked.chosenIndex)}
                    </dd>
                  </div>
                )}
                <div>
                  <dt className="inline vibrant-secondary">Correct answer: </dt>
                  <dd className="inline break-words text-success">
                    {answerText(question, checked.correctIndex)}
                  </dd>
                </div>
              </dl>
              <p className="pl-6 text-sm break-words">{checked.explanation}</p>
              {question.sourceFile && (
                <p className="pl-6 text-xs break-words vibrant-secondary">
                  Source: {question.sourceFile}
                </p>
              )}
            </li>
          );
        })}
      </ol>
    </div>
  </div>
);
export default QuizResults;
