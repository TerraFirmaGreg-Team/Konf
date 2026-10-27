package team.terrafirmagreg.konf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.io.ParsingMode;
import com.electronwill.nightconfig.toml.TomlFormat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

    @Test
    void reconcileMaterializesMissingAndKeepsUserValues() throws Exception {
        val file = dir.resolve("app.toml");
        Files.writeString(file, """
                name = "user-name"
                [[shaders]]
                name = "a"
                """);
        val doc = TomlFormat.instance().createConfig(LinkedHashMap::new);
        TomlFormat.instance().createParser().parse("""
                        # Server name
                        name = "default-name"
                        version = "1.0"
                        [[shaders]]
                        name = "a"
                        brightness = 1
                        [[shaders]]
                        name = "b"
                        brightness = 2
                        """, doc, ParsingMode.REPLACE);
        @Cleanup val cfg = Configs.toml(file);
        cfg.reconcile(doc);
        cfg.sync();
        val text = Files.readString(file);
        assertTrue(text.contains("name = \"user-name\""));
        assertTrue(text.contains("version = \"1.0\""));
        assertTrue(text.contains("name = \"a\"") && text.contains("name = \"b\""));
    }

    @Test
    void reconcileFillsMissingKeysInsideNamedEntry() throws Exception {
        val file = dir.resolve("app.toml");
        Files.writeString(file, """
                [[shaders]]
                name = "a"
                brightness = 5
                """);
        val doc = TomlFormat.instance().createConfig(LinkedHashMap::new);
        TomlFormat.instance().createParser().parse("""
                        [[shaders]]
                        name = "a"
                        brightness = 1
                        gain = 9
                        """, doc, ParsingMode.REPLACE);
        @Cleanup val cfg = Configs.toml(file);
        cfg.reconcile(doc);
        cfg.sync();
        val text = Files.readString(file);
        assertTrue(text.contains("brightness = 5"));
        assertTrue(text.contains("gain = 9"));
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
    void reconcileCopiesUnmodifiableSubtrees() throws Exception {
        val file = dir.resolve("app.toml");
        @Cleanup val cfg = Configs.toml(file);

        val doc = TomlFormat.instance().createConfig(LinkedHashMap::new);
        val sub = TomlFormat.instance().createConfig(LinkedHashMap::new);
        TomlFormat.instance().createParser().parse("enabled = true\n", sub, ParsingMode.REPLACE);
        doc.set("features", sub.unmodifiable());

        cfg.reconcile(doc);
        cfg.set("features.extra", 7);
        cfg.sync();

        val text = Files.readString(file);
        assertTrue(text.contains("enabled = true"), text);
        assertTrue(text.contains("extra = 7"), text);
    }

    @Test
    void mergeByAppendsMissingNamedEntries() throws Exception {
        val file = dir.resolve("merge.toml");
        Files.writeString(file, "[[shaders]]\nname = \"a\"\ngain = 5\n");
        @Cleanup val cfg = Configs.toml(file);

        val a = cfg.config().createSubConfig();
        a.set("name", "a");
        a.set("gain", 1);
        val b = cfg.config().createSubConfig();
        b.set("name", "b");
        b.set("gain", 2);

        List<Config> merged =
                cfg.value("shaders", List.of(a, b)).mergeBy("name").get();
        assertEquals(2, merged.size());
        assertEquals(5, merged.get(0).<Integer>get("gain"));
        assertEquals("b", merged.get(1).get("name"));
    }

    @Test
    void rangeRejectsNonComparableBounds() {
        @Cleanup val cfg = Configs.toml(dir.resolve("r.toml"));
        val re = cfg.value("re", Pattern.compile("x"));
        assertThrows(IllegalArgumentException.class, () -> re.range(Pattern.compile("a"), Pattern.compile("b")));
        assertThrows(NullPointerException.class, () -> re.set(null));
    }

    @Test
    void onChangeListenerMayDeclareNewValues() {
        @Cleanup val cfg = Configs.toml(dir.resolve("l.toml"));
        cfg.value("a", 1).onChange(v -> cfg.value("b", 2));
        cfg.reload();
    }
}
