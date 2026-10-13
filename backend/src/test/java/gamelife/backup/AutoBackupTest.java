package gamelife.backup;

import gamelife.IntegrationTest;
import gamelife.character.CharacterService;
import gamelife.game.GameService;
import gamelife.tasks.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Desktop brief 4 and 9: automatic backups, rotation, verification, restore. */
class AutoBackupTest extends IntegrationTest {
    @Autowired AutoBackupService auto;
    @Autowired TaskService tasks;
    @Autowired GameService game;
    @Autowired CharacterService character;

    @BeforeEach
    void emptyFolder() throws IOException {
        auto.setFolder("");
        Path f = auto.folder();
        if (Files.isDirectory(f)) {
            try (Stream<Path> files = Files.list(f)) {
                for (Path p : files.toList()) Files.delete(p);
            }
        }
    }

    private List<String> files() throws IOException {
        try (Stream<Path> s = Files.list(auto.folder())) {
            return s.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    @Test
    void aBackupIsWrittenInTheDataFolderAndVerified() {
        tasks.tick(tasks.create("Study", 60, List.of("INTELLECT")).id());
        var e = auto.backupNow("manual");
        assertThat(e.valid()).isTrue();
        assertThat(e.events()).isEqualTo(eventCount());
        assertThat(auto.folder()).isEqualTo(Path.of("target/test-data/data/backups").toAbsolutePath().normalize());
        var s = auto.status();
        assertThat(s.defaultFolder()).isTrue();
        assertThat(s.stale()).isFalse();
        assertThat(s.backups()).singleElement().satisfies(b -> assertThat(b.valid()).isTrue());
    }

    @Test
    void staleAfterThreeDaysAndDailyBackupWhenDue() {
        assertThat(auto.status().stale()).isTrue();          // no backup at all
        auto.dailyIfDue();
        assertThat(auto.status().backups()).hasSize(1);
        clock.advance(Duration.ofHours(23));
        auto.dailyIfDue();                                   // not yet a day
        assertThat(auto.status().backups()).hasSize(1);
        clock.advance(Duration.ofHours(1));
        auto.dailyIfDue();
        assertThat(auto.status().backups()).hasSize(2);
        clock.advance(Duration.ofDays(3).plusMinutes(1));
        assertThat(auto.status().stale()).isTrue();
    }

    @Test
    void rotationKeepsTheLast14PlusOnePerWeekFor8Weeks() throws IOException {
        // two backups a day for 12 weeks
        Instant start = clock.instant();
        for (int day = 0; day < 84; day++) {
            for (int h : new int[]{0, 12}) {
                clock.set(start.plus(Duration.ofDays(day)).plus(Duration.ofHours(h)));
                auto.backupNow("daily");
            }
        }
        List<AutoBackupService.Entry> left = auto.list(auto.folder(), false);
        Instant now = clock.instant();
        // the 14 newest: the last 7 days, twice a day
        assertThat(left.subList(0, 14)).allMatch(e -> Duration.between(e.at(), now).toDays() < 7);
        // older ones: exactly one per calendar week, only for the 8 most recent weeks
        var older = left.subList(14, left.size());
        var weeks = older.stream().map(e -> e.at().atZone(java.time.ZoneOffset.UTC).toLocalDate()
                .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))).toList();
        assertThat(weeks).doesNotHaveDuplicates();
        java.time.LocalDate thisWeek = now.atZone(java.time.ZoneOffset.UTC).toLocalDate()
                .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        assertThat(weeks).allMatch(w -> !w.isBefore(thisWeek.minusWeeks(7)));
        assertThat(left.size()).isBetween(14 + 5, 14 + 8);
        // each kept weekly backup is the newest of its week
        for (var e : older) {
            var week = e.at().atZone(java.time.ZoneOffset.UTC).toLocalDate()
                    .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
            Instant sunday = week.plusDays(7).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
            assertThat(Duration.between(e.at(), sunday).toHours()).isLessThan(12 + 1);
        }
        assertThat(files()).allMatch(f -> f.endsWith(".json"));
    }

    @Test
    void restoreGivesBackTheSameBalancesAndStats() {
        tasks.tick(tasks.create("Study", 300, List.of("INTELLECT", "STRENGTH")).id());
        game.build(0, "archer");
        var backup = auto.backupNow("manual");
        var balance = balance();
        var stats = character.compute(ledger.all()).stats();
        // life goes on, then something goes wrong
        clock.advance(Duration.ofMinutes(5));
        tasks.tick(tasks.create("More", 120, List.of("SOCIAL")).id());
        game.sell(0, true);
        assertThat(balance()).isNotEqualTo(balance);

        clock.advance(Duration.ofMinutes(5));
        var check = auto.restore(backup.file());
        assertThat(check.valid()).isTrue();
        assertThat(balance()).isEqualByComparingTo(balance);
        assertThat(character.compute(ledger.all()).stats()).isEqualTo(stats);
        // the data from before the restore was saved first
        assertThat(auto.status().backups()).anyMatch(e -> e.reason().equals("before-restore"));
    }

    @Test
    void aTamperedBackupIsReportedAndRefused() throws IOException {
        tasks.tick(tasks.create("Study", 60, List.of()).id());
        var e = auto.backupNow("manual");
        Path p = auto.folder().resolve(e.file());
        Files.writeString(p, Files.readString(p).replaceFirst("\"xp\" : \"66.00\"", "\"xp\" : \"999.00\""));
        var s = auto.status();
        assertThat(s.backups().get(0).valid()).isFalse();
        assertThat(s.lastValid()).isFalse();
        var before = balance();
        assertThatThrownBy(() -> auto.restore(e.file())).hasMessageContaining("Backup refused");
        assertThat(balance()).isEqualByComparingTo(before);
        assertThatThrownBy(() -> auto.restore("../gamelife.db")).hasMessageContaining("Not a backup file name");
    }

    @Test
    void theFolderCanBeChangedAndReset() throws IOException {
        Path other = Path.of("target/test-data/elsewhere").toAbsolutePath();
        var s = auto.setFolder(other.toString());
        assertThat(s.folder()).isEqualTo(other.normalize().toString());
        assertThat(s.defaultFolder()).isFalse();
        auto.backupNow("manual");
        assertThat(Files.list(other).count()).isPositive();
        assertThatThrownBy(() -> auto.setFolder("relative/path")).hasMessageContaining("full folder path");
        assertThat(auto.setFolder("").defaultFolder()).isTrue();
    }
}
