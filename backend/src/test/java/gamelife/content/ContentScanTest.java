package gamelife.content;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Brief 12b and 14: folder scanning, sorting, sidecar names, warnings; v4 game data reload. */
class ContentScanTest {
    @TempDir Path dir;

    private void png(String rel, int w, int h) throws IOException {
        Path f = dir.resolve(rel);
        Files.createDirectories(f.getParent());
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB), "png", f.toFile());
    }

    private void text(String rel, String content) throws IOException {
        Path f = dir.resolve(rel);
        Files.createDirectories(f.getParent());
        Files.writeString(f, content);
    }

    private ContentService scan() {
        return new ContentService(dir.toString(), new gamelife.game.GameContent("../content",
                gamelife.config.GameConfig.load(Path.of("../config/gamelife.json"))));
    }

    @Test
    void heroLooksAreSortedByNumberAndFollowTheLevel() throws IOException {
        png("hero/hero_10.png", 32, 32);
        png("hero/hero_02.png", 128, 32); // a 4-frame strip
        png("hero/hero_1.png", 32, 32);
        text("hero/hero_02.json", "{\"name\": \"Netrunner\"}");
        ContentService s = scan();
        var c = s.catalog();
        assertThat(c.heroes()).extracting(ContentService.Art::number).containsExactly(1, 2, 10);
        assertThat(c.heroes().get(0).name()).isEqualTo("Hero 1");           // no sidecar: default name
        assertThat(c.heroes().get(1).name()).isEqualTo("Netrunner");
        assertThat(c.heroes().get(1).frames()).isEqualTo(4);
        assertThat(s.heroArt(9, 10).file()).endsWith("hero_1.png");    // levels 1-10: look 1
        assertThat(s.heroArt(11, 10).file()).endsWith("hero_02.png");  // level 11: look 2
        assertThat(s.heroArt(99, 10).file()).endsWith("hero_10.png");  // capped at the last look
        assertThat(c.warnings()).noneMatch(w -> w.startsWith("hero/"));
    }

    @Test
    void badFilesAreWarningsNeverCrashes() throws IOException {
        png("hero/hero_01.png", 32, 32);
        png("hero/hero_1.png", 32, 32);                 // duplicate number
        png("hero/hero_02.png", 50, 32);                // wrong size
        png("hero/knight.png", 32, 32);                 // wrong name
        text("hero/hero_03.png", "not a picture");      // unreadable
        png("items/skin_lonely.png", 32, 32);           // image without JSON
        png("items/skin_bad.png", 32, 32);
        text("items/skin_bad.json", "{ broken");        // invalid JSON
        png("items/skin_ok.png", 32, 32);
        text("items/skin_ok.json", "{\"id\":\"skin_ok\",\"name\":\"Ok\",\"kind\":\"skin\",\"price\":100,\"tokens\":{\"filter\":\"hue-rotate(10deg)\"}}");
        png("items/skin_big.png", 64, 64);
        text("items/skin_big.json", "{\"id\":\"skin_big\",\"name\":\"Big\",\"kind\":\"skin\",\"price\":100}");
        png("items/charm_old.png", 32, 32);              // v3 power items are no longer sold
        text("items/charm_old.json", "{\"id\":\"charm_old\",\"name\":\"Old\",\"kind\":\"charm\",\"price\":10}");

        var c = scan().catalog();
        assertThat(c.heroes()).hasSize(1);
        assertThat(c.items()).extracting(ContentService.Item::id).containsExactly("skin_ok");
        String all = String.join("\n", c.warnings());
        assertThat(all).contains("duplicate number 1")
                .contains("hero_02.png: wrong size 50x32")
                .contains("knight.png: wrong name")
                .contains("hero_03.png: not a readable PNG")
                .contains("skin_lonely.png: shop image without")
                .contains("skin_bad.json: not valid JSON")
                .contains("skin_big.png: wrong size 64x64")
                .contains("charm_old.json: unknown kind");
    }

    @Test
    void addingTwoFilesAddsABuyableItemAfterReload() throws IOException {
        png("hero/hero_01.png", 32, 32);
        ContentService s = scan();
        assertThat(s.catalog().items()).isEmpty();
        png("items/outfit_new.png", 128, 32);
        text("items/outfit_new.json", "{\"id\":\"outfit_new\",\"name\":\"New\",\"kind\":\"outfit\",\"price\":250}");
        s.reload();
        assertThat(s.item("outfit_new")).isNotNull();
        assertThat(s.item("outfit_new").frames()).isEqualTo(4);
    }

    @Test
    void theRealContentFolderHasNoWarnings() {
        var c = new ContentService("../content", new gamelife.game.GameContent("../content",
                gamelife.config.GameConfig.load(Path.of("../config/gamelife.json")))).catalog();
        assertThat(c.warnings()).isEmpty();
        assertThat(c.heroes()).hasSize(3);
        assertThat(c.items()).extracting(ContentService.Item::kind).contains("outfit", "skin", "theme")
                .doesNotContain("weapon", "armor", "charm");
    }

    @Test
    void aBrokenGameFileOnReloadKeepsTheOldDataAndWarns(@TempDir Path game) throws IOException {
        for (String f : new String[]{"towers.json", "waves.json"}) {
            Files.copy(Path.of("../content", f), game.resolve(f));
        }
        Files.createDirectories(game.resolve("maps"));
        Files.copy(Path.of("../content/maps/map_01.json"), game.resolve("maps/map_01.json"));
        var gc = new gamelife.game.GameContent(game.toString(), gamelife.config.GameConfig.load(Path.of("../config/gamelife.json")));
        Files.writeString(game.resolve("towers.json"), "{ broken");
        ContentService s = new ContentService(dir.toString(), gc);
        assertThat(String.join("\n", s.catalog().warnings())).contains("Cannot read the game data")
                .contains("previous game data is still used");
        assertThat(gc.catalog().tower("archer")).isNotNull();
    }
}
