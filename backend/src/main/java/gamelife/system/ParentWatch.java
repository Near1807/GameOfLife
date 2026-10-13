package gamelife.system;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The desktop shell passes its process id (gamelife.parent-pid). If that process disappears without
 * stopping the server (a crash, or killed from the Task Manager), the server stops itself cleanly
 * (exit backup, database closed) instead of running on unseen and keeping the database busy.
 * 0 (npm start, the browser version) turns it off.
 */
@Component
public class ParentWatch {
    private static final Logger log = LoggerFactory.getLogger(ParentWatch.class);
    private final long parentPid;
    private final ConfigurableApplicationContext context;
    private volatile boolean stopping;

    public ParentWatch(@Value("${gamelife.parent-pid:0}") long parentPid, ConfigurableApplicationContext context) {
        this.parentPid = parentPid;
        this.context = context;
    }

    @Scheduled(initialDelay = 2000, fixedDelay = 2000)
    public void check() {
        if (parentPid <= 0 || stopping || parentAlive()) {
            return;
        }
        stopping = true;
        log.warn("The desktop shell (process {}) is gone: stopping the server", parentPid);
        Thread t = new Thread(() -> System.exit(SpringApplication.exit(context, () -> 0)), "gamelife-parent-gone");
        t.setDaemon(false);
        t.start();
    }

    boolean parentAlive() {
        return ProcessHandle.of(parentPid).map(ProcessHandle::isAlive).orElse(false);
    }
}
