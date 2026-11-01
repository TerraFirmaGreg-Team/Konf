package team.terrafirmagreg.konf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import lombok.Cleanup;
import lombok.val;
import org.junit.jupiter.api.Test;
import team.terrafirmagreg.konf.format.TestFormat;

class ConfigViewTest {

    private static ConfigFile file() {
        return Configs.memory(new TestFormat());
    }

    @Test
    void viewDeclaresRootPaths() {
        @Cleanup val cfg = file();
        val v = cfg.view().value("iconSize", 32);
        assertEquals("iconSize", v.path());
        assertEquals(32, v.get());
        assertEquals(32, cfg.<Integer>get("iconSize"));
    }

    @Test
    void viewSectionComposes() {
        @Cleanup val cfg = file();
        val v = cfg.view().section("dump").value("x", "d");
        assertEquals("dump.x", v.path());
    }

    @Test
    void sectionDeclaresPrefixedPaths() {
        @Cleanup val cfg = file();
        val v = cfg.section("dump.assets").value("iconSize", 32);
        assertEquals("dump.assets.iconSize", v.path());
        assertEquals(32, v.get());
        assertEquals(32, cfg.<Integer>get("dump.assets.iconSize"));
    }

    @Test
    void nestedSectionsCompose() {
        @Cleanup val cfg = file();
        val v = cfg.section("a").section("b").value("c", "x");
        assertEquals("a.b.c", v.path());
    }

    @Test
    void declaredViaSectionPassesVerify() {
        @Cleanup val cfg = file();
        cfg.set("dump.assets.iconSize", 99L);
        cfg.section("dump.assets").value("iconSize", 32).get();
        cfg.verify();
    }

    @Test
    void undeclaredSiblingStillFailsVerify() {
        @Cleanup val cfg = file();
        cfg.set("dump.assets.iconSize", 1L);
        cfg.set("dump.assets.asssets", 2L);
        cfg.section("dump.assets").value("iconSize", 32).get();
        assertThrows(InvalidConfigException.class, cfg::verify);
    }

    @Test
    void syncMaterializesSectionValues() {
        @Cleanup val cfg = file();
        cfg.section("s").value("k", 7);
        cfg.sync();
        assertEquals(7, cfg.<Integer>get("s.k"));
    }

    @Test
    void overlayHitsSectionDeclaredKeys() {
        @Cleanup val cfg = file();
        val v = cfg.section("s").value("port", 1);
        cfg.overlay(Map.of("s.port", 42L));
        assertEquals(42, v.get());
    }

    @Test
    void sameLeavesBindUnderDifferentParents() {
        @Cleanup val cfg = file();
        val a = cfg.section("assets.components.lang").value("key", "d");
        val b = cfg.section("data.components.quests").value("key", "d");
        cfg.sync();
        assertEquals("assets.components.lang.key", a.path());
        assertEquals("data.components.quests.key", b.path());
        assertEquals("d", cfg.<String>get("assets.components.lang.key"));
        assertEquals("d", cfg.<String>get("data.components.quests.key"));
        cfg.verify();
    }
}
