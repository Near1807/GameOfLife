import type { Screen } from '../types';

const ITEMS: { id: Screen; glyph: string; label: string }[] = [
  { id: 'home', glyph: '⌂', label: 'home' },
  { id: 'tasks', glyph: '☑', label: 'tasks' },
  { id: 'timer', glyph: '◷', label: 'focus' },
  { id: 'habits', glyph: '↻', label: 'habits' },
  { id: 'settings', glyph: '⚙', label: 'settings' },
];

export function NavBar(props: { screen: Screen; onChange: (s: Screen) => void }) {
  return (
    <nav className="nav">
      {ITEMS.map((i) => (
        <button key={i.id} className={props.screen === i.id ? 'active' : ''} onClick={() => props.onChange(i.id)}>
          <span className="glyph">{i.glyph}</span>
          {i.label}
        </button>
      ))}
    </nav>
  );
}
