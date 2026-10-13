import type { ReactNode } from 'react';
import type { LucideIcon } from 'lucide-react';

/** Glass panel with an optional icon, title and action on the right. */
export function Card(props: {
  title?: string;
  icon?: LucideIcon;
  iconColor?: string;
  action?: ReactNode;
  glow?: boolean;
  className?: string;
  children: ReactNode;
}) {
  const Icon = props.icon;
  return (
    <section className={'card' + (props.glow ? ' glow' : '') + (props.className ? ' ' + props.className : '')}>
      {(props.title || props.action) && (
        <div className="card-head">
          {Icon && (
            <span className="card-icon" style={props.iconColor ? { color: props.iconColor } : undefined}>
              <Icon size={17} />
            </span>
          )}
          <h2 className="card-title">{props.title}</h2>
          {props.action}
        </div>
      )}
      {props.children}
    </section>
  );
}
