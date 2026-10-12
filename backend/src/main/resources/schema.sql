-- GameLife database. One append-only ledger plus a few small tables (brief section 5).
-- Balance, stat XP, streak etc. are NEVER stored: they are computed from ledger_events.

-- The ledger. Rows are only ever inserted, never updated or deleted
-- (except a full restore from a validated backup file).
CREATE TABLE IF NOT EXISTS ledger_events (
    id              INTEGER PRIMARY KEY,      -- sequential, part of the hash
    event_key       TEXT    NOT NULL UNIQUE,  -- refuses a second payout for the same act (brief 3.2)
    type            TEXT    NOT NULL,
    occurred_at     INTEGER NOT NULL,         -- server time, epoch milliseconds
    game_day        TEXT    NOT NULL,         -- ISO date of the game day (day ends at 04:00 local)
    module          TEXT    NOT NULL,
    label           TEXT    NOT NULL,
    xp              TEXT    NOT NULL,         -- decimal with 2 digits, stored as text to stay exact
    currency_delta  TEXT    NOT NULL,         -- decimal with 2 digits
    stat_splits     TEXT    NOT NULL,         -- canonical JSON object {"STAT": "1.50"}
    meta            TEXT    NOT NULL,         -- canonical JSON object
    prev_hash       TEXT    NOT NULL,
    hash            TEXT    NOT NULL
);

-- To-do tasks. Done / not done is NOT stored here: it is read from the ledger.
CREATE TABLE IF NOT EXISTS tasks (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    name             TEXT    NOT NULL,
    expected_minutes INTEGER NOT NULL,
    tags             TEXT    NOT NULL,        -- JSON array of stat ids
    created_at       INTEGER NOT NULL,
    archived         INTEGER NOT NULL DEFAULT 0
);

-- Focus timers. Times are server timestamps (brief 3.2).
CREATE TABLE IF NOT EXISTS timers (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    label           TEXT    NOT NULL,
    task_id         INTEGER,
    planned_minutes INTEGER NOT NULL,
    tags            TEXT    NOT NULL,
    started_at      INTEGER NOT NULL,
    stopped_at      INTEGER,                  -- set when stopped early
    answer          TEXT                      -- NULL (not answered yet), YES or NO
);

-- Habits with the days of the week they are scheduled on.
CREATE TABLE IF NOT EXISTS habits (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        TEXT    NOT NULL,
    days        TEXT    NOT NULL,             -- e.g. "MON,TUE,WED,THU,FRI,SAT,SUN"
    tags        TEXT    NOT NULL,
    created_at  INTEGER NOT NULL,
    archived    INTEGER NOT NULL DEFAULT 0
);
