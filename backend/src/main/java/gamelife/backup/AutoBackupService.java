package gamelife.backup;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import gamelife.common.GameException;
import gamelife.ledger.HashChain;
import gamelife.ledger.LedgerEvent;
import gamelife.time.GameTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Automatic backups (desktop brief 4). A copy of the ledger export is written
 *  - when the app stops (the context closes: the desktop shell's clean shutdown, or Ctrl+C), and
 *  - once a day while it runs (checked every hour),
 * into the backup folder chosen in Settings (default: "backups" in the data folder).
 *
 * Every backup is read back and its hash chain verified. Rotation keeps the 14 newest backups plus
 * the newest one of each of the last 8 calendar weeks; anything else is deleted. Files are named by
 * their time (server clock, UTC), so the rotation never depends on file dates.
 *
 * The chosen folder is stored in settings.json in the data folder (not in the ledger: it is a
 * setting of this computer, not game data).
 */
@Service
public class AutoBackupService {
    private static final Logger log = LoggerFactory.getLogger(AutoBackupService.class);
    private static final Pattern NAME = Pattern.compile("gamelife-backup-(\\d{4}-\\d{2}-\\d{2}T\\d{2}-\\d{2}-\\d{2}Z)-([a-z-]+)\\.json");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss'Z'").withZone(ZoneOffset.UTC);
    static final int KEEP_LAST = 14;
    static final int KEEP_WEEKS = 8;
    static final Duration DAILY = Duration.ofHours(24);
    static final Duration STALE = Duration.ofDays(3);

    private final BackupService backups;
    private final GameTime time;
    private final Path dataDir;
    private final ObjectMapper json;
    private volatile String lastError;

    /** mapper: Spring's own, so a backup has exactly the format of a manual export. */
    public AutoBackupService(BackupService backups, GameTime time, ObjectMapper mapper,
                             @Value("${gamelife.data-dir}") String dataDir) {
        this.json = mapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .enable(SerializationFeature.INDENT_OUTPUT);
        this.backups = backups;
        this.time = time;
        this.dataDir = Path.of(dataDir).toAbsolutePath().normalize();
    }

    /** One backup file. valid: its hash chain checks out. */
    public record Entry(String file, Instant at, String reason, long bytes, int events, boolean valid, String problem) {}

    /** What Settings and Home show. stale: no backup, or the newest is older than 3 days. */
    public record Status(String folder, boolean defaultFolder, Instant lastBackup, Boolean lastValid, boolean stale,
                         String lastError, List<Entry> backups) {}

    // ------------------------------------------------------------------ writing

    /** Writes, verifies and rotates. Returns the new entry. */
    public synchronized Entry backupNow(String reason) {
        Path folder = folder();
        try {
            Files.createDirectories(folder);
            Instant now = time.now();
            String name = "gamelife-backup-" + STAMP.format(now) + "-" + reason + ".json";
            Path tmp = folder.resolve(name + ".tmp");
            Files.write(tmp, json.writeValueAsBytes(backups.export()));
            Files.move(tmp, folder.resolve(name), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            Entry e = verify(folder.resolve(name));
            if (!e.valid()) {
                throw new IOException("the backup just written does not verify: " + e.problem());
            }
            rotate(folder, now);
            lastError = null;
            log.info("Backup written: {}", folder.resolve(name));
            return e;
        } catch (IOException | RuntimeException ex) {
            lastError = "Backup failed in " + folder + ": " + ex.getMessage();
            log.error(lastError, ex);
            throw GameException.conflict(lastError);
        }
    }

    /** Every hour: a backup when the newest is a day old (or there is none). */
    @Scheduled(initialDelayString = "${gamelife.backup.check-delay-ms:60000}", fixedDelayString = "${gamelife.backup.check-every-ms:3600000}")
    public void dailyIfDue() {
        Instant last = newest().map(Entry::at).orElse(null);
        if (last == null || Duration.between(last, time.now()).compareTo(DAILY) >= 0) {
            try {
                backupNow("daily");
            } catch (RuntimeException e) {
                // already logged and shown in Settings
            }
        }
    }

    /** When the app stops cleanly (desktop shell exit, Ctrl+C): a backup first. */
    @EventListener(ContextClosedEvent.class)
    public void onExit() {
        try {
            backupNow("exit");
        } catch (RuntimeException e) {
            // already logged; never block the shutdown
        }
    }

    // ------------------------------------------------------------------ rotation

    /**
     * Keeps the KEEP_LAST newest backups, plus the newest backup of each of the KEEP_WEEKS most
     * recent calendar weeks (Monday to Sunday, UTC), counting the current week. Deletes the rest.
     */
    void rotate(Path folder, Instant now) throws IOException {
        List<Entry> all = list(folder, false);  // newest first
        Set<String> keep = new HashSet<>();
        all.stream().limit(KEEP_LAST).forEach(e -> keep.add(e.file()));
        LocalDate thisWeek = weekOf(now);
        Map<LocalDate, Entry> newestPerWeek = new LinkedHashMap<>();
        for (Entry e : all) {
            LocalDate w = weekOf(e.at());
            if (!w.isBefore(thisWeek.minusWeeks(KEEP_WEEKS - 1)) && !w.isAfter(thisWeek)) {
                newestPerWeek.putIfAbsent(w, e);
            }
        }
        newestPerWeek.values().forEach(e -> keep.add(e.file()));
        for (Entry e : all) {
            if (!keep.contains(e.file())) {
                Files.deleteIfExists(folder.resolve(e.file()));
            }
        }
    }

    private static LocalDate weekOf(Instant t) {
        return t.atZone(ZoneOffset.UTC).toLocalDate().with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
    }

    // ------------------------------------------------------------------ reading

    public Status status() {
        Path folder = folder();
        List<Entry> all;
        try {
            all = list(folder, true);
        } catch (IOException e) {
            all = List.of();
        }
        Entry last = all.isEmpty() ? null : all.get(0);
        boolean stale = last == null || Duration.between(last.at(), time.now()).compareTo(STALE) > 0;
        return new Status(folder.toString(), folder.equals(defaultFolder()), last == null ? null : last.at(),
                last == null ? null : last.valid(), stale, lastError, all);
    }

    java.util.Optional<Entry> newest() {
        try {
            return list(folder(), false).stream().findFirst();
        } catch (IOException e) {
            return java.util.Optional.empty();
        }
    }

    /** Backup files of a folder, newest first. verifyAll: also check each one's hash chain. */
    List<Entry> list(Path folder, boolean verifyAll) throws IOException {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        List<Entry> out = new ArrayList<>();
        try (Stream<Path> files = Files.list(folder)) {
            for (Path p : files.toList()) {
                Matcher m = NAME.matcher(p.getFileName().toString());
                if (!m.matches()) {
                    continue;
                }
                Instant at = STAMP.parse(m.group(1), Instant::from);
                out.add(verifyAll ? verify(p) : new Entry(p.getFileName().toString(), at, m.group(2), Files.size(p), -1, true, null));
            }
        }
        out.sort(Comparator.comparing(Entry::at).reversed().thenComparing(Entry::file));
        return out;
    }

    /** Reads a backup file and checks its format and hash chain. */
    Entry verify(Path p) {
        Matcher m = NAME.matcher(p.getFileName().toString());
        Instant at = m.matches() ? STAMP.parse(m.group(1), Instant::from) : Instant.EPOCH;
        String reason = m.matches() ? m.group(2) : "";
        try {
            BackupFile f = json.readValue(p.toFile(), BackupFile.class);
            if (!BackupFile.FORMAT.equals(f.format())) {
                return new Entry(p.getFileName().toString(), at, reason, Files.size(p), 0, false, "not a GameLife backup");
            }
            List<LedgerEvent> events = f.events().stream().map(BackupService::fromRow).toList();
            HashChain.Check check = HashChain.verify(events);
            return new Entry(p.getFileName().toString(), at, reason, Files.size(p), events.size(), check.valid(),
                    check.valid() ? null : check.message());
        } catch (IOException | RuntimeException e) {
            long size = 0;
            try {
                size = Files.size(p);
            } catch (IOException ignored) {
                // keep 0
            }
            return new Entry(p.getFileName().toString(), at, reason, size, 0, false, "unreadable: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ restore

    /**
     * Restores one backup of the folder (one button in Settings). The chain is verified first; a
     * broken file changes nothing. The current data is backed up first ("before-restore"), so a
     * restore can itself be undone.
     */
    public synchronized HashChain.Check restore(String fileName) {
        Path folder = folder();
        if (fileName == null || !NAME.matcher(fileName).matches()) {
            throw GameException.badRequest("Not a backup file name");
        }
        Path p = folder.resolve(fileName);
        if (!Files.isRegularFile(p)) {
            throw GameException.notFound("No backup " + fileName + " in " + folder);
        }
        Entry e = verify(p);
        if (!e.valid()) {
            throw GameException.badRequest("Backup refused: " + e.problem());
        }
        BackupFile file;
        try {
            file = json.readValue(p.toFile(), BackupFile.class);
        } catch (IOException ex) {
            throw GameException.badRequest("Backup unreadable: " + ex.getMessage());
        }
        backupNow("before-restore");
        return backups.importBackup(file);
    }

    // ------------------------------------------------------------------ folder setting

    public Path defaultFolder() {
        return dataDir.resolve("backups");
    }

    public Path folder() {
        Path settings = dataDir.resolve("settings.json");
        try {
            if (Files.exists(settings)) {
                Object f = json.readValue(settings.toFile(), Map.class).get("backupFolder");
                if (f != null && !f.toString().isBlank()) {
                    return Path.of(f.toString()).toAbsolutePath().normalize();
                }
            }
        } catch (IOException | RuntimeException e) {
            log.warn("Cannot read {}: {}", settings, e.getMessage());
        }
        return defaultFolder();
    }

    /** Sets the backup folder (blank = back to the default). It must exist or be creatable, and be writable. */
    public synchronized Status setFolder(String path) {
        Path settings = dataDir.resolve("settings.json");
        try {
            Map<String, Object> s = Files.exists(settings) ? json.readValue(settings.toFile(), Map.class) : new LinkedHashMap<>();
            if (path == null || path.isBlank()) {
                s.remove("backupFolder");
            } else {
                Path p = Path.of(path.trim());
                if (!p.isAbsolute()) {
                    throw GameException.badRequest("Choose a full folder path, for example C:\\Users\\you\\OneDrive\\GameLife");
                }
                Files.createDirectories(p);
                Path probe = Files.createTempFile(p, "gamelife-", ".probe");
                Files.delete(probe);
                s.put("backupFolder", p.toAbsolutePath().normalize().toString());
            }
            Files.createDirectories(dataDir);
            Files.write(settings, json.writeValueAsBytes(s));
        } catch (IOException e) {
            throw GameException.badRequest("Cannot use this folder: " + e.getMessage());
        }
        return status();
    }
}
