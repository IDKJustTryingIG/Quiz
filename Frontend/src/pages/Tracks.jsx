import PageHeader from "@/components/PageHeader";
import SurfaceCard from "@/components/SurfaceCard";
// Placeholder content — replace with real tracks from the API.
const tracks = [
  {
    slug: "arrays",
    name: "Arrays and hashing",
    detail: "Two pointers, prefix sums, frequency maps.",
    meta: "4 of 12 solved",
  },
  {
    slug: "binary-search",
    name: "Binary search",
    detail: "Boundaries, rotated arrays, search on answer.",
    meta: "Not started · 9 problems",
  },
  {
    slug: "linked-lists",
    name: "Linked lists",
    detail: "Reversal, cycle detection, merging.",
    meta: "Not started · 8 problems",
  },
  {
    slug: "trees",
    name: "Trees",
    detail: "Traversal, depth, lowest common ancestor.",
    meta: "Not started · 14 problems",
  },
  {
    slug: "graphs",
    name: "Graphs",
    detail: "BFS, DFS, topological order, union find.",
    meta: "Locked · finish Trees first",
  },
];
const Tracks = () => (
  <section className="mx-auto max-w-5xl px-4 py-16">
    <PageHeader
      title="Tracks"
      description="Each track is ordered, so every problem assumes only what came before it. Work down the list."
    />
    <div className="grid gap-3">
      {tracks.map(({ slug, ...track }) => (
        <SurfaceCard
          key={slug}
          to={`/tracks/${slug}`}
          orientation="row"
          {...track}
        />
      ))}
    </div>
  </section>
);
export default Tracks;
