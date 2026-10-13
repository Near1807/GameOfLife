package gamelife;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Entry point of the GameLife backend. Scheduling: daily backups and the wave catch-up. */
@SpringBootApplication
@EnableScheduling
public class GameLifeApplication {
    public static void main(String[] args) {
        SpringApplication.run(GameLifeApplication.class, args);
    }
}
