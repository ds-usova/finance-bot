// A guilloche rosette of the kind engraved on passbook covers and banknotes: one ellipse, turned through a
// full circle in small steps, so the overlapping strokes weave the pattern.
const TURNS = Array.from({ length: 36 }, (_, index) => index * 5);

export type RosetteProps = {
  className?: string;
};

export function Rosette({ className }: RosetteProps) {
  return (
    <svg
      viewBox="-100 -100 200 200"
      aria-hidden="true"
      focusable="false"
      className={className}
      fill="none"
      stroke="currentColor"
    >
      {TURNS.map((angle) => (
        <ellipse
          key={angle}
          rx="92"
          ry="34"
          transform={`rotate(${String(angle)})`}
          strokeWidth="0.45"
        />
      ))}
      {TURNS.map((angle) => (
        <ellipse
          key={`inner-${String(angle)}`}
          rx="46"
          ry="17"
          transform={`rotate(${String(angle + 2.5)})`}
          strokeWidth="0.35"
        />
      ))}
      <circle r="97" strokeWidth="0.8" />
      <circle r="99.5" strokeWidth="0.4" />
    </svg>
  );
}
