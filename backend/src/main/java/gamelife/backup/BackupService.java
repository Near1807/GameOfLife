package gamelife.backup;

import gamelife.common.GameException;
import gamelife.common.Money;
import gamelife.habits.HabitRepository;
import gamelife.ledger.EventType;
import gamelife.ledger.HashChain;
import gamelife.ledger.LedgerEvent;
import gamelife.ledger.LedgerRestore;
import gamelife.ledger.LedgerService;
import gamelife.tasks.TaskRepository;
import gamelife.time.GameTime;
import gamelife.timer.TimerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Export everything to one JSON file, and restore from it (brief 3.3 and 5).
 * Import validates the hash chain BEFORE touching the database, then replaces all data in one
 * transaction: either everything is restored or nothing changes.
 */
@Service
public class BackupService {
    private final LedgerService ledger;
    private final LedgerRestore restore;
    private final TaskRepository tasks;
    private final HabitRepository habits;
    private final TimerRepository timers;
    private final GameTime time;

    public BackupService(LedgerService ledger, LedgerRestore restore, TaskRepository tasks, HabitRepository habits,
                         TimerRepository timers, GameTime time) {
        this.ledger = ledger;
        this.restore = restore;
        this.tasks = tasks;
        this.habits = habits;
        this.timers = timers;
        this.time = time;
    }

    public BackupFile export() {
        List<BackupFile.EventRow> rows = ledger.all().stream().map(BackupService::toRow).toList();
        return new BackupFile(BackupFile.FORMAT, BackupFile.VERSION, time.now().toString(), rows,
                tasks.findAll(), habits.findAll(), timers.findAll(), List.of());
    }

    /** Restores a backup. Refused (nothing changes) if the format is wrong or the chain is broken. */
    @Transactional
    public synchronized HashChain.Check importBackup(BackupFile file) {
        if (file == null || !BackupFile.FORMAT.equals(file.format())) {
            throw GameException.badRequest("This is not a GameLife backup file");
        }
        if (file.version() > BackupFile.VERSION) {
            throw GameException.badRequest("Backup version " + file.version() + " is newer than this app");
        }
        List<LedgerEvent> events;
        try {
            events = file.events().stream().map(BackupService::fromRow).toList();
        } catch (RuntimeException e) {
            throw GameException.badRequest("Backup file is damaged: " + e.getMessage());
        }
        HashChain.Check check = HashChain.verify(events);
        if (!check.valid()) {
            throw GameException.badRequest("Backup refused, hash chain broken: " + check.message());
        }

        restore.replaceAll(events);
        tasks.deleteAllForRestore();
        habits.deleteAllForRestore();
        timers.deleteAllForRestore();
        file.tasks().forEach(tasks::insertWithId);
        file.habits().forEach(habits::insertWithId);
        file.timers().forEach(timers::insertWithId);
        return ledger.verify();
    }

    static BackupFile.EventRow toRow(LedgerEvent e) {
        return new BackupFile.EventRow(e.id(), e.key(), e.type().name(), e.occurredAt().toEpochMilli(),
                e.gameDay().toString(), e.module(), e.label(), e.xp().toPlainString(),
                e.currencyDelta().toPlainString(), e.statSplits(), e.meta(), e.prevHash(), e.hash());
    }

    static LedgerEvent fromRow(BackupFile.EventRow r) {
        return new LedgerEvent(r.id(), r.key(), EventType.valueOf(r.type()), Instant.ofEpochMilli(r.occurredAt()),
                LocalDate.parse(r.gameDay()), r.module(), r.label(), Money.parse(r.xp()),
                Money.parse(r.currencyDelta()), r.statSplits(), r.meta(), r.prevHash(), r.hash());
    }
}
