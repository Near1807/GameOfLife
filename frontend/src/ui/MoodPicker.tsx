import { Annoyed, Frown, Laugh, Meh, Smile } from 'lucide-react';

const MOODS = [
  { Icon: Frown, color: '#f87171', label: 'rough' },
  { Icon: Annoyed, color: '#fb923c', label: 'meh' },
  { Icon: Meh, color: '#94a3b8', label: 'okay' },
  { Icon: Smile, color: '#22d3ee', label: 'good' },
  { Icon: Laugh, color: '#34d399', label: 'great' },
];

/** Five clean icons on a colour scale (v2 section 2). One tap = the check-in. */
export function MoodPicker(props: { onPick: (mood: number) => void; picked?: number | null }) {
  return (
    <div>
      <div className="moods">
        {MOODS.map((m, i) => (
          <button
            key={m.label}
            className="mood-btn"
            style={{ '--c': m.color, ...(props.picked === i + 1 ? { background: `color-mix(in srgb, ${m.color} 20%, transparent)` } : {}) } as React.CSSProperties}
            aria-label={`mood ${i + 1}: ${m.label}`}
            disabled={props.picked != null}
            onClick={() => props.onPick(i + 1)}
          >
            <m.Icon size={28} strokeWidth={1.8} />
          </button>
        ))}
      </div>
      <div className="mood-scale" />
    </div>
  );
}

export function moodIcon(mood: number) {
  return MOODS[Math.max(0, Math.min(4, mood - 1))];
}
