package gamelife.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;

/**
 * Wires the three external things the game depends on: the config file, the SQLite file
 * and the clock. Paths come from application.properties and can be overridden on the
 * command line, e.g. --gamelife.db-path=/somewhere/else.db
 */
@Configuration
public class AppConfig {

    @Bean
    public GameConfig gameConfig(@Value("${gamelife.config-path}") String configPath) {
        return GameConfig.load(Path.of(configPath));
    }

    /**
     * The server clock. Every timestamp in the ledger comes from here, never from the
     * client (brief 3.2: "Elapsed time comes from the server's timestamps").
     * Tests replace this bean with a clock they can move.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** SQLite data source. Creates the folder of the database file if needed. */
    @Bean
    public DataSource dataSource(@Value("${gamelife.db-path}") String dbPath) throws IOException {
        Path file = Path.of(dbPath).toAbsolutePath();
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        SQLiteConfig sqlite = new SQLiteConfig();
        sqlite.setBusyTimeout(5000); // wait instead of failing if two writes overlap
        SQLiteDataSource ds = new SQLiteDataSource(sqlite);
        ds.setUrl("jdbc:sqlite:" + file);
        return ds;
    }
}
