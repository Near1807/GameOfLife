/**
 * Automatic backups in Settings (desktop brief 4): the status, a damaged backup cannot be
 * restored, a restore takes one button with one confirmation tap, and the desktop-only controls
 * are hidden in the browser.
 */
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { BackupStatus } from '../api';

vi.mock('../api', async (importOriginal) => {
  const original = await importOriginal<typeof import('../api')>();
  return { ...original, api: { backupStatus: vi.fn(), backupNow: vi.fn(), setBackupFolder: vi.fn(), restoreBackup: vi.fn() } };
});

import { api } from '../api';
import { BackupCard } from '../ui/BackupCard';

const hour = 3600e3;
const status = (over: Partial<BackupStatus> = {}): BackupStatus => ({
  folder: 'C:\\Users\\t\\AppData\\Roaming\\GameLife\\backups', defaultFolder: true,
  lastBackup: new Date(Date.now() - 2 * hour).toISOString(), lastValid: true, stale: false, lastError: null,
  backups: [
    { file: 'gamelife-backup-a-exit.json', at: new Date(Date.now() - 2 * hour).toISOString(), reason: 'exit', bytes: 100, events: 42, valid: true, problem: null },
    { file: 'gamelife-backup-b-daily.json', at: new Date(Date.now() - 30 * hour).toISOString(), reason: 'daily', bytes: 100, events: 0, valid: false, problem: 'chain broken at event 7' },
  ],
  ...over,
});

beforeEach(() => vi.clearAllMocks());

describe('automatic backups', () => {
  it('shows the last backup and its check', async () => {
    vi.mocked(api.backupStatus).mockResolvedValue(status());
    render(<BackupCard onMessage={() => {}} refresh={() => {}} />);
    expect(await screen.findByText(/Last backup 2 hours ago, verified/)).toBeTruthy();
    expect(screen.getByText(/42 events, chain verified/)).toBeTruthy();
    expect(screen.getByText(/damaged: chain broken at event 7/)).toBeTruthy();
    // in the browser there is no folder picker and no "open folder"
    expect(screen.queryByLabelText('choose a folder')).toBeNull();
  });

  it('restores with one button and one confirmation; a damaged backup cannot be chosen', async () => {
    vi.mocked(api.backupStatus).mockResolvedValue(status());
    vi.mocked(api.restoreBackup).mockResolvedValue({ valid: true, message: 'ok' });
    const user = userEvent.setup();
    render(<BackupCard onMessage={() => {}} refresh={() => {}} />);
    const buttons = await screen.findAllByRole('button', { name: /Restore/ });
    expect((buttons[1] as HTMLButtonElement).disabled).toBe(true);
    await user.click(buttons[0]);
    expect(api.restoreBackup).not.toHaveBeenCalled();
    await user.click(screen.getByRole('button', { name: /Tap again to restore/ }));
    expect(api.restoreBackup).toHaveBeenCalledWith('gamelife-backup-a-exit.json');
  });

  it('says when there is no backup yet', async () => {
    vi.mocked(api.backupStatus).mockResolvedValue(status({ backups: [], lastBackup: null, lastValid: null, stale: true }));
    render(<BackupCard onMessage={() => {}} refresh={() => {}} />);
    expect(await screen.findByText('No backup yet')).toBeTruthy();
  });
});
