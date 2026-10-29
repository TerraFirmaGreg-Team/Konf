package team.terrafirmagreg.konf.yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.nio.file.Path;
import lombok.Cleanup;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import team.terrafirmagreg.konf.Configs;
import team.terrafirmagreg.konf.format.Formats;

class YamlFormatTest {

    @TempDir
    Path dir;

    @Test
    void registered() {
        assertInstanceOf(YamlFormat.class, Formats.byName("yaml"));
        assertInstanceOf(YamlFormat.class, Formats.detect(Path.of("app.yaml")));
        assertInstanceOf(YamlFormat.class, Formats.detect(Path.of("app.yml")));
        assertFalse(YamlFormat.INSTANCE.commented());
    }

    @Test
    void roundTrip() throws Exception {
        val file = dir.resolve("app.yaml");
        @Cleanup val cfg = Configs.yaml(file);
        cfg.value("s.port", 8080);
        cfg.sync();
        @Cleanup val reopened = Configs.of(file);
        assertInstanceOf(YamlFormat.class, reopened.format());
        assertEquals(8080, reopened.value("s.port", 0).get());
    }
}
