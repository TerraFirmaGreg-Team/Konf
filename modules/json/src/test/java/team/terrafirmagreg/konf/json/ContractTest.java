package team.terrafirmagreg.konf.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import lombok.Cleanup;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import team.terrafirmagreg.konf.Configs;
import team.terrafirmagreg.konf.InvalidConfigException;

class ContractTest {

    @TempDir
    Path dir;

    @Test
    void strictThrowsOnInvalidValue() throws Exception {
        val file = dir.resolve("s.json");
        Files.writeString(file, "{\"port\": \"text\"}");
        @Cleanup val cfg = Configs.builder(file).strict().open();
        val port = cfg.value("port", 0);
        assertThrows(InvalidConfigException.class, port::get);
    }

    @Test
    void strictThrowsOnClampedValue() throws Exception {
        val file = dir.resolve("c.json");
        Files.writeString(file, "{\"port\": 99999999999}");
        @Cleanup val cfg = Configs.builder(file).strict().open();
        val port = cfg.value("port", 0);
        assertThrows(InvalidConfigException.class, port::get);
    }

    @Test
    void nonStrictResetsInvalidValue() throws Exception {
        val file = dir.resolve("n.json");
        Files.writeString(file, "{\"port\": \"text\"}");
        @Cleanup val cfg = Configs.json(file);
        assertEquals(0, cfg.value("port", 0).get());
    }

    @Test
    void jsonNullResetsToDefault() throws Exception {
        val file = dir.resolve("n.json");
        Files.writeString(file, "{\"a\": null, \"b\": 1}");
        @Cleanup val cfg = Configs.json(file);
        assertEquals("x", cfg.value("a", "x").get());
        assertEquals(1, cfg.value("b", 0).get());
    }

    @Test
    void verifyFlagsUndeclaredKeys() throws Exception {
        val file = dir.resolve("v.json");
        Files.writeString(file, "{\"id\": \"x\", \"extra\": 1}");
        @Cleanup val cfg = Configs.json(file);
        cfg.value("id", "d").get();
        cfg.value("extra", 0).get();
        cfg.verify();
    }

    @Test
    void verifyThrowsOnUndeclaredKeys() throws Exception {
        val file = dir.resolve("v.json");
        Files.writeString(file, "{\"id\": \"x\", \"extra\": 1}");
        @Cleanup val cfg = Configs.json(file);
        cfg.value("id", "d").get();
        assertThrows(InvalidConfigException.class, cfg::verify);
    }
}
