package team.terrafirmagreg.konf.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import lombok.Cleanup;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import team.terrafirmagreg.konf.Configs;
import team.terrafirmagreg.konf.InvalidConfigException;
import team.terrafirmagreg.konf.format.Formats;

class JsonFormatTest {

    @TempDir
    Path dir;

    @Test
    void registered() {
        assertInstanceOf(JsonFormat.class, Formats.byName("json"));
        assertInstanceOf(JsonFormat.class, Formats.detect(Path.of("app.JSON")));
        assertFalse(JsonFormat.INSTANCE.commented());
    }

    @Test
    void roundTrip() throws Exception {
        val file = dir.resolve("app.json");
        @Cleanup val cfg = Configs.json(file);
        cfg.value("s.port", 8080);
        cfg.sync();
        @Cleanup val reopened = Configs.of(file);
        assertInstanceOf(JsonFormat.class, reopened.format());
        assertEquals(8080, reopened.value("s.port", 0).get());
    }

    @Test
    void strictModeThrowsOnInvalidValues() throws Exception {
        val file = dir.resolve("contract.json");
        Files.writeString(file, "{\"iconSize\": \"big\", \"id\": \"pack\"}");
        @Cleanup
        val cfg = Configs.builder(file).format(JsonFormat.INSTANCE).strict().open();

        val size = cfg.value("iconSize", 32);
        assertThrows(InvalidConfigException.class, size::get);

        assertEquals("pack", cfg.require("id"));
        assertEquals(0, cfg.value("missing", 0).get());
        assertThrows(InvalidConfigException.class, () -> cfg.require("absent"));
    }

    @Test
    void verifyRejectsUndeclaredKeys() throws Exception {
        val file = dir.resolve("contract.json");
        Files.writeString(file, "{\"id\": \"x\", \"dump\": {\"assets\": {}, \"asssets\": 1}}");
        @Cleanup
        val cfg = Configs.builder(file).format(JsonFormat.INSTANCE).strict().open();

        cfg.require("id");
        val dump = cfg.at("dump");
        dump.subconfig("assets");
        dump.value("iconSize", 32).get();

        var e = assertThrows(InvalidConfigException.class, cfg::verify);
        assertTrue(e.getMessage().contains("dump.asssets"), e.getMessage());
    }

    @Test
    void scopedViewAndSubconfigVerifyPass() {
        @Cleanup val cfg = Configs.memory(JsonFormat.INSTANCE);
        cfg.set("dump.assets.iconSize", 16);
        cfg.set("minecraft.loaders.forge", "1.20.1");

        val dump = cfg.at("dump");
        assertEquals(16, dump.<Integer>require("assets.iconSize"));
        val loaders = cfg.subconfig("minecraft.loaders");
        assertEquals("1.20.1", loaders.<String>get("forge"));

        cfg.verify();
    }
}
