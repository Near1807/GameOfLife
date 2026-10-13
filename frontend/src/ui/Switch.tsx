export function Switch(props: { checked: boolean; onChange: () => void; label: string }) {
  return (
    <label className="switch" aria-label={props.label}>
      <input type="checkbox" checked={props.checked} onChange={props.onChange} />
      <span />
    </label>
  );
}
