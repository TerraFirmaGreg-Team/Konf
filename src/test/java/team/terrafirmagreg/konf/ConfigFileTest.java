package team.terrafirmagreg.konf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.Cleanup;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigFileTest {

    @TempDir
    Path dir;

    enum Level {
        LOW,
        HIGH
    }

    @Test
    void writesDefaultsWithCommentsOnSync() throws Exception {
        val file = dir.resolve("app.toml");
        @Cleanup val cfg = Configs.toml(file);
        val port = cfg.value("server.port", 25565).range(1, 65535).comment("Listen port");
        cfg.sync();
        val text = Files.readString(file);
        assertTrue(text.contains("port = 25565"), text);
        assertTrue(text.contains("# Listen port"), text);
        assertEquals(25565, port.get());
    }

    @Test
    void readsExistingValues() throws Exception {
        val file = dir.resolve("app.toml");
        Files.writeString(file, "[server]\nport = 1234\n");
        @Cleanup val cfg = Configs.toml(file);
        val port = cfg.value("server.port", 25565);
        assertEquals(1234, port.get());
    }

    @Test
    void clampsOutOfRangeAndWritesBack() throws Exception {
        val file = dir.resolve("app.toml");
        Files.writeString(file, "port = 99999\n");
        @Cleanup val cfg = Configs.toml(file);
        val port = cfg.value("port", 25565).range(1, 65535);
        assertEquals(65535, port.get());
        cfg.sync();
        assertTrue(Files.readString(file).contains("port = 65535"));
    }

    @Test
    void resetsInvalidTypeToDefault() throws Exception {
        val file = dir.resolve("app.toml");
        Files.writeString(file, "port = \"abc\"\n");
        @Cleanup val cfg = Configs.toml(file);
        val port = cfg.value("port", 25565);
        assertEquals(25565, port.get());
    }

    @Test
    void enumRoundTripAndFallback() throws Exception {
        val file = dir.resolve("app.toml");
        @Cleanup val cfg = Configs.toml(file);
        val level = cfg.value("level", Level.LOW);
        level.set(Level.HIGH);
        assertEquals(Level.HIGH, level.get());
        assertTrue(Files.readString(file).contains("level = \"HIGH\""));
        Files.writeString(file, "level = \"NOPE\"\n");
        cfg.reload();
        assertEquals(Level.LOW, level.get());
    }

    @Test
    void setPersistsImmediately() throws Exception {
        val file = dir.resolve("app.toml");
        @Cleanup val cfg = Configs.toml(file);
        val port = cfg.value("server.port", 25565);
        port.set(8080);
        assertTrue(Files.readString(file).contains("8080"));
    }

    @Test
    void firesOnChangeOnReload() throws Exception {
        val file = dir.resolve("app.toml");
        @Cleanup val cfg = Configs.toml(file);
        val seen = new ArrayList<Integer>();
        cfg.value("port", 1).onChange(seen::add);
        Files.writeString(file, "port = 2\n");
        cfg.reload();
        assertEquals(List.of(2), seen);
    }

    @Test
    void stringListRoundTrip() throws Exception {
        val file = dir.resolve("app.toml");
        @Cleanup val cfg = Configs.toml(file);
        val names = cfg.value("names", List.of("a", "b"));
        cfg.sync();
        names.set(List.of("x", "y"));
        assertEquals(List.of("x", "y"), names.get());
    }

    @Test
    void rejectsValidatorOnSet() {
        val file = dir.resolve("app.toml");
        @Cleanup val cfg = Configs.toml(file);
        val port = cfg.value("port", 25565).validate(v -> v > 0);
        assertThrows(IllegalArgumentException.class, () -> port.set(-1));
    }
}
