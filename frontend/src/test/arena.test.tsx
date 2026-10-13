import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Arena } from '../ui/Arena';
import { fight, look } from './fixtures';

// jsdom has no ResizeObserver
globalThis.ResizeObserver ??= class { observe() {} disconnect() {} unobserve() {} } as unknown as typeof ResizeObserver;

describe('battle arena (brief 11)', () => {
  const arena = (over = {}, recap = null as Parameters<typeof Arena>[0]['recap']) =>
    render(<Arena fight={{ ...fight, ...over }} look={look} hits={[]} recap={recap} onDismissRecap={() => {}} onOpen={() => {}} />);

  it('shows both health bars and the boss name', () => {
    arena();
    expect(screen.getByRole('progressbar', { name: 'boss health' }).getAttribute('aria-valuenow')).toBe('59'); // 470 / 800
    expect(screen.getByRole('progressbar', { name: 'hero health' }).getAttribute('aria-valuenow')).toBe('82'); // 90 / 110
    expect(screen.getByText(/Bug Queen/, { selector: '.hud-name' })).toBeTruthy();
  });

  it("shows today's shield and the hit expected tonight", () => {
    arena();
    expect(screen.getByRole('progressbar', { name: 'shield today' }).getAttribute('aria-valuenow')).toBe('40');
    expect(screen.getByText(/Tonight Bug Queen hits for/).textContent).toContain('12');
  });

  it('says when today is fully shielded or protected', () => {
    arena({ shieldToday: 1, expectedHit: 0 });
    expect(screen.getByText(/Fully shielded/)).toBeTruthy();
  });

  it('shows the defeat counter on the boss', () => {
    arena({ defeatsByThisBoss: 2 });
    expect(screen.getByText('Defeated you 2×')).toBeTruthy();
  });

  it('shows the recap after a night', () => {
    const onDismiss = vi.fn();
    render(<Arena fight={fight} look={look} hits={[]} onOpen={() => {}} onDismissRecap={onDismiss}
      recap={{ id: 5, days: 1, damage: 12, blocked: 8, heal: 0, fell: false, heroHpBefore: 102 }} />);
    const text = screen.getByRole('status').textContent ?? '';
    expect(text).toContain('While you were away');
    expect(text).toContain('hit for 12');
    expect(text).toContain('blocked 8');
    screen.getByRole('button', { name: 'OK' }).click();
    expect(onDismiss).toHaveBeenCalledOnce();
  });
});
