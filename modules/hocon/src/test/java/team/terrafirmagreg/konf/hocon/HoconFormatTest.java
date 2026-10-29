package team.terrafirmagreg.konf.hocon;

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

class HoconFormatTest {

    @TempDir
    Path dir;

    @Test
    void registered() {
        assertInstanceOf(HoconFormat.class, Formats.byName("hocon"));
        assertInstanceOf(HoconFormat.class, Formats.detect(Path.of("app.conf")));
        assertInstanceOf(HoconFormat.class, Formats.detect(Path.of("app.hocon")));
        assertTrue(HoconFormat.INSTANCE.commented());
    }

    @Test
    void roundTrip() throws Exception {
        val file = dir.resolve("app.conf");
        @Cleanup val cfg = Configs.hocon(file);
        cfg.value("s.port", 8080);
        cfg.sync();
        @Cleanup val reopened = Configs.of(file);
        assertInstanceOf(HoconFormat.class, reopened.format());
        assertEquals(8080, reopened.value("s.port", 0).get());
    }

    @Test
    void blankLineSeparatesEntries() throws Exception {
        val file = dir.resolve("spaced.conf");
        @Cleanup val cfg = Configs.hocon(file);
        cfg.value("a", 1).comment("first");
        cfg.value("b", 2).comment("second");
        cfg.sync();
        assertTrue(Files.readString(file).contains("a: 1\n\n# second"), Files.readString(file));
    }
}
