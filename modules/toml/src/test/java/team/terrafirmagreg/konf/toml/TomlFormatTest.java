package team.terrafirmagreg.konf.toml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import lombok.Cleanup;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import team.terrafirmagreg.konf.Configs;
import team.terrafirmagreg.konf.format.Formats;

class TomlFormatTest {

    @TempDir
    Path dir;

    enum Mode {
        NORMAL,
        CREATIVE
    }

    @Test
    void registered() {
        assertInstanceOf(TomlFormat.class, Formats.byName("toml"));
        assertInstanceOf(TomlFormat.class, Formats.detect(Path.of("app.TOML")));
        assertTrue(TomlFormat.INSTANCE.commented());
    }

    @Test
    void roundTrip() throws Exception {
        val file = dir.resolve("app.toml");
        @Cleanup val cfg = Configs.toml(file);
        cfg.value("s.port", 8080).comment("cmt");
        cfg.value("s.mode", Mode.CREATIVE);
        cfg.sync();

        val text = Files.readString(file);
        assertTrue(text.contains("# cmt"), text);

        @Cleanup val reopened = Configs.of(file);
        assertInstanceOf(TomlFormat.class, reopened.format());
        assertEquals(8080, reopened.value("s.port", 0).get());
        assertEquals(Mode.CREATIVE, reopened.value("s.mode", Mode.NORMAL).get());
    }
}
