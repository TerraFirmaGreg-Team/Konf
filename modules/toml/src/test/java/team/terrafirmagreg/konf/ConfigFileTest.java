package team.terrafirmagreg.konf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
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
    void resetsFractionalNumberToDefault() throws Exception {
        val file = dir.resolve("app.toml");
        Files.writeString(file, "port = 5.7\n");
        @Cleanup val cfg = Configs.toml(file);
        val port = cfg.value("port", 25565);
        assertEquals(25565, port.get());
    }

    @Test
    void resetsIntOverflowToDefault() throws Exception {
        val file = dir.resolve("app.toml");
        Files.writeString(file, "port = 99999999999\n");
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
        cfg.load();
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

    @Test
    void multilineStringContentSurvivesRoundTrip() throws Exception {
        val file = dir.resolve("multi.toml");
        @Cleanup val cfg = Configs.toml(file);
        cfg.value("text", "").set("alpha\n[fake-table]\n# not a comment\nomega");
        cfg.value("other", 1).comment("next entry");
        cfg.sync();

        val written = Files.readString(file);
        assertTrue(written.contains("\"\"\"\n\n# next entry"), written);

        @Cleanup val reopened = Configs.toml(file);
        assertEquals(
                "alpha\n[fake-table]\n# not a comment\nomega",
                reopened.value("text", "").get());
    }

    @Test
    void rangeRejectsNonComparableBounds() {
        @Cleanup val cfg = Configs.toml(dir.resolve("r.toml"));
        val re = cfg.value("re", Pattern.compile("x"));
        assertThrows(IllegalArgumentException.class, () -> re.range(Pattern.compile("a"), Pattern.compile("b")));
        assertThrows(NullPointerException.class, () -> re.set(null));
    }

    @Test
    void genericRejectsNonNativeDefaults() {
        @Cleanup val cfg = Configs.toml(dir.resolve("g.toml"));
        assertThrows(
                IllegalArgumentException.class,
                () -> cfg.value("m", Map.of("k", 1)).get());
        assertThrows(
                IllegalArgumentException.class,
                () -> cfg.value("o", new Object()).get());
    }

    @Test
    void nullDefaultRejectedAtDeclaration() {
        @Cleanup val cfg = Configs.toml(dir.resolve("n.toml"));
        assertThrows(NullPointerException.class, () -> cfg.value("s", (String) null));
    }

    @Test
    void directLoadKeepsOverlayPrecedence() throws Exception {
        val file = dir.resolve("o.toml");
        Files.writeString(file, "port = 1\n");
        @Cleanup val cfg = Configs.toml(file);
        cfg.overlay(Map.of("port", "8080"));
        Files.writeString(file, "port = 5\n");
        cfg.load();
        assertEquals(8080, cfg.value("port", 0).get());
    }

    @Test
    void removeDeletesKeys() {
        @Cleanup val cfg = Configs.toml(dir.resolve("rm.toml"));
        cfg.set("a.b", 1);
        cfg.set("a.c", 2);
        cfg.remove("a.b");
        assertFalse(cfg.contains("a.b"));
        assertTrue(cfg.contains("a.c"));
    }
}
