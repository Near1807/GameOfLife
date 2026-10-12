package gamelife.backup;

import gamelife.ledger.HashChain;
import gamelife.time.GameTime;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * GET  /api/backup/export  downloads gamelife-backup-YYYY-MM-DD.json
 * POST /api/backup/import  body = the JSON file, replaces all data after validation
 */
@RestController
@RequestMapping("/api/backup")
public class BackupController {
    private final BackupService service;
    private final GameTime time;

    public BackupController(BackupService service, GameTime time) {
        this.service = service;
        this.time = time;
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
