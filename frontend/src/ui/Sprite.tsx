/**
 * A pixel-art sprite sheet (brief 12): one horizontal strip of square frames, played at `fps`.
 * Scaled by a whole number and never smoothed.
 */
export function Sprite(props: {
  src: string;
  size: number; // frame size in pixels: 32 for the hero, 64 for bosses
  scale: number;
  frames?: number;
  fps?: number;
  className?: string;
  label?: string;
}) {
  const frames = props.frames ?? 4;
  const w = props.size * props.scale;
  return (
    <div
      className={'sprite anim' + (props.className ? ' ' + props.className : '')}
      role="img"
      aria-label={props.label}
      style={
        {
          width: w,
          height: w,
          backgroundImage: `url('/${props.src}')`,
          backgroundSize: `${w * frames}px ${w}px`,
          '--frame-w': `${w}px`,
          '--frames': frames,
          animationDuration: `${frames / (props.fps ?? 6)}s`,
        } as React.CSSProperties
      }
    />
  );
}
