import { Sprite } from './Sprite';

/**
 * The pixel-art hero shown as framed artwork inside the modern UI (v2 section 3).
 * `hitKey` changing replays a small attack lunge.
 */
export function HeroPortrait(props: { scale?: number; hitKey?: number; sprite: string; frames: number }) {
  const scale = props.scale ?? 4;
  const frame = 32 * scale + 24;
  return (
    <div className="portrait" style={{ width: frame, height: frame }}>
      <Sprite key={props.hitKey} src={props.sprite} frames={props.frames} size={32} scale={scale}
        className={props.hitKey ? 'attack' : undefined} label="your hero" />
    </div>
  );
}
