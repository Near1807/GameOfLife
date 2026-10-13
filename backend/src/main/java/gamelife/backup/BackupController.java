package gamelife.backup;

import gamelife.ledger.HashChain;
import gamelife.time.GameTime;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * GET  /api/backup/export   downloads gamelife-backup-YYYY-MM-DD.json
 * POST /api/backup/import   body = the JSON file, replaces all data after validation
 * GET  /api/backup/auto     automatic backups: folder, list, status
 * POST /api/backup/auto     a backup now
 * PUT  /api/backup/folder   {folder} the backup folder ("" = default)
 * POST /api/backup/restore  {file} restores one automatic backup (chain checked first)
 */
@RestController
@RequestMapping("/api/backup")
public class BackupController {
    private final BackupService service;
    private final AutoBackupService auto;
    private final GameTime time;

    public BackupController(BackupService service, AutoBackupService auto, GameTime time) {
        this.service = service;
        this.auto = auto;
        this.time = time;
    }

    public record FolderInput(String folder) {}

    public record RestoreInput(String file) {}

    @GetMapping("/auto")
    public AutoBackupService.Status status() {
        return auto.status();
    }

    @PostMapping("/auto")
    public AutoBackupService.Entry backupNow() {
        return auto.backupNow("manual");
    }

    @PutMapping("/folder")
    public AutoBackupService.Status folder(@RequestBody FolderInput in) {
        return auto.setFolder(in.folder());
    }

    @PostMapping("/restore")
    public HashChain.Check restore(@RequestBody RestoreInput in) {
        return auto.restore(in.file());
    }

    @GetMapping("/export")
    public ResponseEntity<BackupFile> export() {
        String filename = "gamelife-backup-" + time.today() + ".json";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(service.export());
    }

    @PostMapping("/import")
    public HashChain.Check importBackup(@RequestBody BackupFile file) {
        return service.importBackup(file);
    }
}
