import { Link } from "react-router";
/**
 * Content surfaces are bordered rather than translucent: a light material
 * stacked on another light material collapses legibility (§12), and the
 * chrome above already spends the app's translucency budget.
 */
const SurfaceCard = ({
  to,
  name,
  detail,
  meta,
  icon,
  orientation = "stacked",
}) => (
  <Link
    to={to}
    className="group flex items-start gap-4 sm:gap-6 rounded-2xl border p-5 transition-[transform,border-color] duration-100 ease-out will-change-transform hover:border-foreground/25 active:scale-[0.985] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
  >
    {icon && (
      <span className="mt-0.5 grid size-9 shrink-0 place-items-center rounded-xl border [&>svg]:size-4">
        {icon}
      </span>
    )}
    <span className="min-w-0 flex-1">
      <span className="block font-medium">{name}</span>
      <span className="mt-1 block text-sm vibrant-secondary">{detail}</span>
      {meta && orientation === "stacked" && (
        <span className="mt-3 block text-xs vibrant-secondary">{meta}</span>
      )}
    </span>
    {meta && orientation === "row" && (
      <span className="shrink-0 self-center text-sm vibrant-secondary">
        {meta}
      </span>
    )}
  </Link>
);
export default SurfaceCard;
