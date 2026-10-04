import PageHeader from "@/components/PageHeader";
// Placeholder content — replace with real results from the API.
const recent = [
  { date: "Yesterday", topic: "Arrays and hashing", score: "8 of 10" },
  { date: "3 days ago", topic: "Binary search", score: "6 of 10" },
  { date: "Last week", topic: "Mixed review", score: "9 of 10" },
];
const Quiz = () => (
  <section className="mx-auto max-w-5xl px-4 py-16">
    <PageHeader
      title="Quiz"
      description="Ten timed questions drawn from what you have already practised. No hints, no going back."
    />

    <button
      type="button"
      className="w-full rounded-2xl border p-6 text-left transition-[transform,border-color] duration-100 ease-out hover:border-foreground/25 active:scale-[0.99] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
    >
      <span className="block text-xl font-medium">Start a quiz</span>
      <span className="mt-1 block text-sm vibrant-secondary">
        10 questions · about 6 minutes · scored at the end
      </span>
    </button>

    <h2 className="mt-14 mb-4 text-sm font-medium vibrant-secondary">
      Recent attempts
    </h2>
    <dl className="grid gap-px overflow-hidden rounded-2xl border">
      {recent.map(({ date, topic, score }) => (
        <div
          key={date}
          className="flex flex-wrap items-baseline justify-between gap-x-6 gap-y-1 border-b px-5 py-4 last:border-b-0"
        >
          <dt className="font-medium">{topic}</dt>
          <dd className="text-sm vibrant-secondary">
            {score} · {date}
          </dd>
        </div>
      ))}
    </dl>
  </section>
);
export default Quiz;
