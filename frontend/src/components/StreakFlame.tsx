/**
 * Streak flame (brief 11): grows at the milestones 7, 30, 100 and 365 days.
 * Pixel art scales only by whole numbers (brief 12).
 */
export function StreakFlame(props: { days: number }) {
  const scale = props.days >= 365 ? 5 : props.days >= 100 ? 4 : props.days >= 30 ? 3 : props.days >= 7 ? 2 : 1;
  const size = 16 * (props.days > 0 ? scale + 1 : 1);
  return (
    <img
      src="/ui/icon_flame.png"
      className="pixel flame"
      width={size}
      height={size}
      alt=""
      style={{ opacity: props.days > 0 ? 1 : 0.35 }}
    />
  );
}
