/**
 * The pixel-art hero (brief 11). Idle animation from the sprite sheet; when `attackKey`
 * changes the hero lunges (validating an action "attacks"). The boss arrives in milestone 3.
 */
export function Hero(props: { attackKey: number }) {
  return (
    <div
      key={props.attackKey}
      className={'hero pixel' + (props.attackKey > 0 ? ' attack' : '')}
      role="img"
      aria-label="your hero"
    />
  );
}
