import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { BossCard, timeLeft } from '../ui/BossCard';
import { stage } from './fixtures';

describe('boss card (v2 4.4)', () => {
  it('shows the boss, its remaining HP and a health bar at the right level', () => {
    render(<BossCard stage={stage} />);
    expect(screen.getByText('Bug Queen')).toBeTruthy();
    expect(screen.getByText(/\/ 800 HP/)).toBeTruthy();
    expect(screen.getByRole('progressbar', { name: 'boss health' }).getAttribute('aria-valuenow')).toBe('59'); // 470 / 800
  });

  it('the health bar drops after a payout (more damage)', () => {
    const { rerender } = render(<BossCard stage={stage} />);
    rerender(<BossCard stage={{ ...stage, damage: 630, hp: 170 }} />);
    expect(screen.getByRole('progressbar', { name: 'boss health' }).getAttribute('aria-valuenow')).toBe('21');
  });

  it('a defeated boss shows the stamp, the coin reward and the overkill', () => {
    render(<BossCard stage={{ ...stage, damage: 880, hp: 0, overkill: 80, cleared: true }} />);
    expect(screen.getByText('Defeated')).toBeTruthy();
    expect(screen.getByText(/\+100 coins/).textContent).toContain('overkill 80');
  });

  it('counts down to the end of the week', () => {
    const in2d = new Date(Date.now() + 2 * 86_400_000 + 3 * 3_600_000 + 60_000).toISOString();
    expect(timeLeft(in2d)).toBe('2d 3h left');
  });
});
