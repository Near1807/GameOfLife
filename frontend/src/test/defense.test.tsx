/**
 * The map screen (v4 brief 4 and 5): build, upgrade and sell in at most 2 taps once the spot is
 * picked (sell asks one confirmation), and the replay shows the result the server returned.
 * The API is mocked: these tests only look at the interface.
 */
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ActionResult, DefenseView, Watch } from '../api';
import type { Act } from '../types';

vi.mock('../api', async (importOriginal) => {
  const original = await importOriginal<typeof import('../api')>();
  return {
    ...original,
    api: { game: vi.fn(), buildTower: vi.fn(), upgradeTower: vi.fn(), sellTower: vi.fn(), unlockSpot: vi.fn(),
      runWave: vi.fn(), replay: vi.fn() },
  };
});

import { api } from '../api';
import { DefenseCard } from '../ui/DefenseCard';

const archer = {
  type: 'archer', name: 'Archer', kind: 'Physical, single target', role: 'attack' as const, color: '#5af0b4', shape: 'dot',
  a: 0, b: 0, paid: 100,
  stats: { dmg: 12, rate: 1.1, range: 125, rad: 0, slow: 0, burn: 0, pois: 0, chain: 0, multi: 1, pierce: 0, rbuff: 0, dbuff: 0, coin: 0, disc: 0 },
  paths: [
    { name: 'Sniper', tier: 0, locked: false, price: 90, next: '+50% damage, +25 range', tiers: [] },
    { name: 'Rapid', tier: 0, locked: false, price: 80, next: '+50% fire rate', tiers: [] },
  ],
};

const view: DefenseView = {
  mapId: 'map_01', mapName: 'Neon Crossing', width: 960, height: 540, path: [[-20, 120], [980, 120]], base: [940, 120],
  spots: [
    { index: 0, x: 130, y: 185, open: true, tower: archer },
    { index: 1, x: 300, y: 200, open: true, tower: null },
    { index: 2, x: 150, y: 300, open: false, tower: null },
  ],
  towers: [
    { id: 'archer', name: 'Archer', kind: 'Physical', role: 'attack', color: '#5af0b4', shape: 'dot', price: 100, listPrice: 100, locked: null },
    { id: 'cannon', name: 'Cannon', kind: 'Area', role: 'attack', color: '#ffb454', shape: 'square', price: 180, listPrice: 180, locked: 'Needs Strength 3' },
  ],
  wave: 3, furthestCleared: 2, livesMax: 10, status: 'waiting', nextAt: new Date(Date.now() + 3600e3).toISOString(),
  attemptsToday: 2, maxAttemptsPerDay: 16, balance: 500, incomeMultiplier: 1.03, discount: 0.005, nextSpotCost: 150,
  last: { key: 'wave-attempt:2:1', wave: 2, attempt: 1, won: true, manual: false, at: '2026-03-02T12:00:00Z', lives: 10,
    livesMax: 10, enemies: 7, killed: 7, leaked: 0, seconds: 8.4 },
  recent: [], enemies: { grunt: { name: 'Grunt', color: '#f00', radius: 7, air: false, armor: 0, ward: 0 } }, stats: {}, statEffects: [],
};

const ok: ActionResult = { event: null, paid: true, message: '' };
const act = vi.fn<Act>(async (p) => p);

beforeEach(() => {
  vi.mocked(api.game).mockResolvedValue(view);
  vi.mocked(api.buildTower).mockResolvedValue(ok);
  vi.mocked(api.upgradeTower).mockResolvedValue(ok);
  vi.mocked(api.sellTower).mockResolvedValue(ok);
  act.mockClear();
});

describe('map friction', () => {
  it('builds a tower in 2 taps: the spot, then the tower', async () => {
    const user = userEvent.setup();
    render(<DefenseCard act={act} version={0} />);
    await user.click(await screen.findByLabelText('spot 2: empty'));
    await user.click(screen.getByLabelText('build Archer for 100'));
    expect(api.buildTower).toHaveBeenCalledWith(1, 'archer');
    expect((screen.getByLabelText('build Cannon for 180') as HTMLButtonElement | null)?.disabled).toBe(true);
    expect(screen.getByText('Needs Strength 3')).toBeTruthy();
  });

  it('upgrades in 2 taps: the tower, then the path', async () => {
    const user = userEvent.setup();
    render(<DefenseCard act={act} version={0} />);
    await user.click(await screen.findByLabelText('spot 1: Archer'));
    await user.click(screen.getByText('Rapid'));
    expect(api.upgradeTower).toHaveBeenCalledWith(0, 1, 1);
  });

  it('sells only after one confirmation tap', async () => {
    const user = userEvent.setup();
    render(<DefenseCard act={act} version={0} />);
    await user.click(await screen.findByLabelText('spot 1: Archer'));
    await user.click(screen.getByText('Sell'));
    expect(api.sellTower).not.toHaveBeenCalled();
    await user.click(screen.getByText('Tap again to sell'));
    expect(api.sellTower).toHaveBeenCalledWith(0);
  });

  it('a locked spot offers its unlock price', async () => {
    const user = userEvent.setup();
    render(<DefenseCard act={act} version={0} />);
    await user.click(await screen.findByLabelText('spot 3: locked'));
    expect(screen.getByText('Unlock this spot')).toBeTruthy();
  });
});

describe('replay', () => {
  it('ends on the result the server returned', async () => {
    const watch: Watch = {
      attempt: { key: 'wave-attempt:2:1', wave: 2, attempt: 1, won: false, manual: false, at: '' },
      result: { wave: 2, won: false, lives: 0, livesMax: 10, enemies: 7, killed: 3, leaked: 4, seconds: 9,
        replay: { fps: 10, types: ['grunt'], frames: [{ t: 0.1, lives: 10, enemies: [[0, 10, 120, 100, 0]], shots: [], chains: [], pops: [] }] } },
      sameAsRecorded: true,
    };
    vi.mocked(api.replay).mockResolvedValue(watch);
    const user = userEvent.setup();
    render(<DefenseCard act={act} version={0} />);
    await user.click(await screen.findByText('Watch last attempt'));
    expect(await screen.findByText('Wave 2 failed')).toBeTruthy();
    expect(screen.getByText(/3\/7 defeated · 0\/10 lives left/)).toBeTruthy();
  });

  it('shows the status: next attempt or stuck', async () => {
    vi.mocked(api.game).mockResolvedValue({ ...view, status: 'stuck' });
    render(<DefenseCard act={act} version={0} />);
    expect(await screen.findByText(/Stuck on wave 3/)).toBeTruthy();
    expect((screen.getByText('Run now').closest('button') as HTMLButtonElement | null)?.disabled).toBe(true);
  });
});
