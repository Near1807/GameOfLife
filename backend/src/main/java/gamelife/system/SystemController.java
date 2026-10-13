package gamelife.system;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/**
 * Clean shutdown for the desktop shell (desktop brief 1). The shell starts the server with a random
 * token (gamelife.shutdown-token) and posts it here on exit: the Spring context closes normally, so
 * the exit backup is written and SQLite is closed, which a killed process could not do. Without a
 * token (npm start, the browser version) the endpoint does not exist (404).
 */
@RestController
public class SystemController {
    private static final Logger log = LoggerFactory.getLogger(SystemController.class);
    private final String token;
    private final ConfigurableApplicationContext context;

    public SystemController(@Value("${gamelife.shutdown-token:}") String token, ConfigurableApplicationContext context) {
        this.token = token;
        this.context = context;
    }

    @PostMapping("/api/system/shutdown")
    public ResponseEntity<String> shutdown(@RequestHeader(value = "X-GameLife-Token", required = false) String given) {
        if (token.isBlank()) {
            return ResponseEntity.notFound().build();
        }
        if (given == null || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("bad token");
        }
        log.info("Shutdown requested by the desktop shell");
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(200); // let the answer leave first
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            System.exit(SpringApplication.exit(context, () -> 0));
        }, "gamelife-shutdown");
        t.setDaemon(false);
        t.start();
        return ResponseEntity.accepted().body("stopping");
    }
}
