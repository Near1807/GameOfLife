package gamelife.game;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Missed wave attempts (desktop brief 5). The catch-up is lazy and exact (WaveService.sync): every
 * slot that passed while the computer was off is worked out once, in order, with the build as it
 * was at that time. It already runs on every read of the game; this also runs it as soon as the
 * server is ready, and every 10 minutes while the app stays open without being looked at.
 */
@Component
public class WaveCatchUp {
    private static final Logger log = LoggerFactory.getLogger(WaveCatchUp.class);
    private final WaveService waves;

    public WaveCatchUp(WaveService waves) {
        this.waves = waves;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void atStartup() {
        run();
    }

    @Scheduled(initialDelayString = "${gamelife.waves.check-every-ms:600000}", fixedDelayString = "${gamelife.waves.check-every-ms:600000}")
    public void run() {
        try {
            waves.sync();
        } catch (RuntimeException e) {
            log.error("Wave catch-up failed", e);
        }
    }
}
