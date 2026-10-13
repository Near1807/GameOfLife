package gamelife.content;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Brief 12b and 14: folder scanning, sorting, loop-around, sidecar names, warnings. */
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
        return new ContentService(dir.toString());
    }

    @Test
    void bossesAreSortedByNumberWithSidecarNames() throws IOException {
        png("enemies/boss_10.png", 64, 64);
        png("enemies/boss_02.png", 256, 64); // a 4-frame strip
        png("enemies/boss_1.png", 64, 64);
        text("enemies/boss_02.json", "{\"name\": \"Doomscroll Wraith\", \"title\": \"It feeds\"}");
        var c = scan().catalog();
        assertThat(c.bosses()).extracting(ContentService.Art::number).containsExactly(1, 2, 10);
        assertThat(c.bosses().get(0).name()).isEqualTo("Boss 1");           // no sidecar: default name
        assertThat(c.bosses().get(1).name()).isEqualTo("Doomscroll Wraith");
        assertThat(c.bosses().get(1).frames()).isEqualTo(4);
        assertThat(c.warnings()).noneMatch(w -> w.startsWith("enemies/"));
    }

    @Test
    void loopsAroundPastTheLastBossAndPicksHeroAndRegion() throws IOException {
        png("enemies/boss_01.png", 64, 64);
        png("enemies/boss_02.png", 64, 64);
        png("enemies/boss_03.png", 64, 64);
        png("hero/hero_01.png", 32, 32);
        png("hero/hero_02.png", 128, 32);
        png("regions/region_01.png", 320, 180);
        png("regions/region_02.png", 320, 180);
        ContentService s = scan();
        assertThat(s.bossArt(4).file()).endsWith("boss_01.png"); // boss 4 -> first picture again
        assertThat(s.bossArt(6).file()).endsWith("boss_03.png");
        assertThat(s.heroArt(9, 10).file()).endsWith("hero_01.png");  // levels 1-10: look 1
        assertThat(s.heroArt(11, 10).file()).endsWith("hero_02.png"); // level 11: look 2
        assertThat(s.heroArt(99, 10).file()).endsWith("hero_02.png"); // capped at the last look
        assertThat(s.regionArt(3, 3).file()).endsWith("region_01.png"); // bosses 1-3: region 1
        assertThat(s.regionArt(4, 3).file()).endsWith("region_02.png");
        assertThat(s.regionArt(7, 3).file()).endsWith("region_01.png"); // loops
    }

    @Test
    void badFilesAreWarningsNeverCrashes() throws IOException {
        png("enemies/boss_01.png", 64, 64);
        png("enemies/boss_1.png", 64, 64);              // duplicate number
        png("enemies/boss_02.png", 50, 64);             // wrong size
        png("enemies/dragon.png", 64, 64);              // wrong name
        text("enemies/boss_03.png", "not a picture");   // unreadable
        text("enemies/boss_04.gif", "x");               // wrong format
        png("items/weapon_lonely.png", 32, 32);         // image without JSON
        png("items/charm_bad.png", 32, 32);
        text("items/charm_bad.json", "{ broken");       // invalid JSON
        png("items/charm_ok.png", 32, 32);
        text("items/charm_ok.json", "{\"id\":\"charm_ok\",\"name\":\"Ok\",\"kind\":\"charm\",\"price\":100,\"effect\":\"shieldBonus\",\"value\":0.05}");
        png("items/charm_big.png", 64, 64);
        text("items/charm_big.json", "{\"id\":\"charm_big\",\"name\":\"Big\",\"kind\":\"charm\",\"price\":100}");
        text("items/hat.json", "{\"id\":\"hat\",\"name\":\"Hat\",\"kind\":\"hat\",\"price\":10}");

        var c = scan().catalog();
        assertThat(c.bosses()).hasSize(1);
        assertThat(c.items()).extracting(ContentService.Item::id).containsExactly("charm_ok");
        String all = String.join("\n", c.warnings());
        assertThat(all).contains("duplicate number 1")
                .contains("boss_02.png: wrong size 50x64")
                .contains("dragon.png: wrong name")
                .contains("boss_03.png: not a readable PNG")
                .contains("boss_04.gif: wrong name or format")
                .contains("weapon_lonely.png: shop image without")
                .contains("charm_bad.json: not valid JSON")
                .contains("charm_big.png: wrong size 64x64")
                .contains("hat.png")
                .contains("hero/: no hero_NN.png found");
    }

    @Test
    void addingTwoFilesAddsABuyableItemAfterReload() throws IOException {
        png("enemies/boss_01.png", 64, 64);
        ContentService s = scan();
        assertThat(s.catalog().items()).isEmpty();
        png("items/armor_new.png", 32, 32);
        text("items/armor_new.json", "{\"id\":\"armor_new\",\"name\":\"New\",\"kind\":\"armor\",\"price\":250,\"bonus\":{\"maxHp\":15}}");
        s.reload();
        assertThat(s.item("armor_new")).isNotNull();
        assertThat(s.item("armor_new").bonus().maxHp()).isEqualTo(15.0);
    }

    @Test
    void theRealContentFolderHasNoWarnings() {
        var c = new ContentService("../content").catalog();
        assertThat(c.warnings()).isEmpty();
        assertThat(c.bosses()).hasSize(10);
        assertThat(c.heroes()).hasSize(3);
        assertThat(c.regions()).hasSize(4);
        assertThat(c.items()).extracting(ContentService.Item::kind).contains("weapon", "armor", "charm", "outfit", "skin", "theme");
    }
}
