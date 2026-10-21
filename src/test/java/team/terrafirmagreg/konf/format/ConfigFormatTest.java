package team.terrafirmagreg.konf.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ConfigFormatTest {

    @Test
    void detectsByExtensionAndAliases() {
        assertEquals(ConfigFormat.TOML, ConfigFormat.detect(Path.of("app.toml")));
        assertEquals(ConfigFormat.JSON, ConfigFormat.detect(Path.of("app.json")));
        assertEquals(ConfigFormat.HOCON, ConfigFormat.detect(Path.of("app.conf")));
        assertEquals(ConfigFormat.HOCON, ConfigFormat.detect(Path.of("app.hocon")));
        assertEquals(ConfigFormat.YAML, ConfigFormat.detect(Path.of("app.yaml")));
        assertEquals(ConfigFormat.YAML, ConfigFormat.detect(Path.of("app.yml")));
    }

    @Test
    void detectIsCaseInsensitive() {
        assertEquals(ConfigFormat.TOML, ConfigFormat.detect(Path.of("APP.TOML")));
    }

    @Test
    void throwsOnUnknownExtension() {
        assertThrows(IllegalArgumentException.class, () -> ConfigFormat.detect(Path.of("app.ini")));
        assertThrows(IllegalArgumentException.class, () -> ConfigFormat.detect(Path.of("app")));
    }

    @Test
    void commentedFlagMatchesWriterCapabilities() {
        assertTrue(ConfigFormat.TOML.commented());
        assertTrue(ConfigFormat.HOCON.commented());
        assertFalse(ConfigFormat.JSON.commented());
        assertFalse(ConfigFormat.YAML.commented());
    }
}
