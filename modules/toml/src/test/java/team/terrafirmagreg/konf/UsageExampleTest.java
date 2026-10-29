package team.terrafirmagreg.konf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.electronwill.nightconfig.core.io.ParsingMode;
import com.electronwill.nightconfig.core.io.WritingMode;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.Cleanup;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import team.terrafirmagreg.konf.toml.TomlFormat;
import team.terrafirmagreg.konf.value.ValueCodec;

class UsageExampleTest {

    @TempDir
    Path dir;

    enum Mode {
        NORMAL,
        CREATIVE
    }

    @Test
    void fullTomlExample() throws Exception {
        @Cleanup
        val cfg = Configs.builder(dir.resolve("server.toml"))
                .defaultResource("/defaults/server.toml")
                .open();

        val debug = cfg.value("general.debug", false).comment("Debug output");
        val port = cfg.value("server.port", 25565).range(1, 65535).comment("Listen port");
        val ratio = cfg.value("server.ratio", 1.0).range(0, 10);
        val seed = cfg.value("world.seed", 42L).range(0, Long.MAX_VALUE);
        val motd = cfg.value("server.motd", "Hello").validate(s -> !s.isBlank());
        val mode = cfg.value("server.mode", Mode.NORMAL);
        val names = cfg.value("server.names", List.of("alpha", "beta"));
        val modes = cfg.value("server.modes", List.of(Mode.NORMAL), ValueCodec.ofEnum(Mode.class));
        val timeout = cfg.value("server.timeout", Duration.ofMinutes(5));

        assertEquals(1234, port.get());

        val fired = new ArrayList<>();
        cfg.value("server.pretty", true).onChange(fired::add);

        port.set(8080);
        cfg.sync();

        val text = Files.readString(cfg.path());
        assertTrue(text.contains("port = 8080"), text);
        assertTrue(text.contains("# Debug output"), text);
        assertTrue(text.contains("ratio = 1.0"), text);
        assertTrue(text.contains("motd = \"Hello\""), text);
        assertTrue(text.contains("mode = \"NORMAL\""), text);
        assertTrue(text.contains("timeout = \"PT5M\""), text);
        assertTrue(text.contains("modes = [ \"NORMAL\"]") || text.contains("[\"NORMAL\"]"), text);

        Files.writeString(cfg.path(), text.replace("port = 8080", "port = 9090"));
        cfg.reload();
        assertEquals(9090, port.get());
        assertEquals(List.of(true), fired);

        assertTrue(cfg.contains("server.port"));
        cfg.set("misc.note", "freeform");
        assertEquals("freeform", cfg.get("misc.note"));

        assertEquals(Mode.NORMAL, mode.get());
        assertEquals(List.of(Mode.NORMAL), modes.get());
        assertEquals(Duration.ofMinutes(5), timeout.get());
        assertEquals(1.0, ratio.get());
        assertEquals(42L, seed.get());
        assertEquals("Hello", motd.get());
        assertEquals(List.of("alpha", "beta"), names.get());
        assertEquals(false, debug.get());
    }

    @Test
    void multilineCommentsRenderPerLine() throws Exception {
        val file = dir.resolve("multi.toml");
        @Cleanup val cfg = Configs.toml(file);
        cfg.value("k", 1).comment("First line of explanation", "Second line — why it matters", "Third line");
        cfg.sync();

        val text = Files.readString(file);
        assertTrue(text.contains("# First line of explanation"), text);
        assertTrue(text.contains("# Second line — why it matters"), text);
        assertTrue(text.contains("# Third line"), text);
    }

    @Test
    void blankLineSeparatesEntries() throws Exception {
        val file = dir.resolve("spaced.toml");
        @Cleanup val cfg = Configs.toml(file);
        cfg.value("a", 1).comment("first");
        cfg.value("b", 2).comment("second");
        cfg.value("nested.c", 3).comment("third");
        cfg.sync();

        val text = Files.readString(file);
        assertTrue(text.contains("a = 1\n\n# second"), text);
        assertTrue(text.contains("b = 2\n\n["), text);
    }

    @Test
    void unreadableCodecValueResetsToDefault() throws Exception {
        val file = dir.resolve("codec.toml");
        Files.writeString(file, "timeout = \"not-a-duration\"\nmodes = [\"NORMAL\", \"NOPE\"]\n");
        @Cleanup val cfg = Configs.toml(file);

        val timeout =
                cfg.value("timeout", Duration.ofMinutes(5), ValueCodec.ofString(Duration::parse, Duration::toString));
        val modes = cfg.value("modes", List.of(Mode.NORMAL), ValueCodec.ofEnum(Mode.class));

        assertEquals(Duration.ofMinutes(5), timeout.get());
        assertEquals(List.of(Mode.NORMAL), modes.get());
        cfg.sync();
        assertTrue(Files.readString(file).contains("timeout = \"PT5M\""));
    }

    @Test
    void validatorRejectsFileValueOnRead() throws Exception {
        val file = dir.resolve("valid.toml");
        Files.writeString(file, "port = -5\n");
        @Cleanup val cfg = Configs.toml(file);
        assertEquals(25565, cfg.value("port", 25565).validate(v -> v > 0).get());
    }

    @Test
    void defaultDataCopiesSiblingFile() throws Exception {
        val defaults = dir.resolve("defaults.toml");
        Files.writeString(defaults, "port = 7777\n");

        val file = dir.resolve("app.toml");
        @Cleanup val cfg = Configs.builder(file).defaultData(defaults).open();
        assertEquals(7777, cfg.value("port", 1).get());
    }

    @Test
    void builderPassthroughOptions() throws Exception {
        val file = dir.resolve("opts.toml");
        @Cleanup
        val cfg = Configs.builder(file)
                .charset(StandardCharsets.UTF_8)
                .writingMode(WritingMode.REPLACE)
                .parsingMode(ParsingMode.REPLACE)
                .open();
        val motd = cfg.value("motd", "Привет");
        cfg.sync();
        assertEquals("Привет", motd.get());
    }

    @Test
    void memoryConfig() {
        @Cleanup val cfg = Configs.memory(TomlFormat.INSTANCE);
        val port = cfg.value("server.port", 25565);
        assertNull(cfg.path());
        assertEquals(25565, port.get());
        port.set(8080);
        assertEquals(8080, port.get());
        cfg.sync();
    }

    @Test
    void builtInTypesRoundTrip() {
        @Cleanup val cfg = Configs.memory(TomlFormat.INSTANCE);

        val f = cfg.value("f", 1.5f);
        f.set(9.5f);
        assertEquals(9.5f, f.get());

        assertEquals(
                LocalDate.of(2024, 1, 15),
                cfg.value("date", LocalDate.of(2024, 1, 15)).get());
        assertEquals(
                Duration.ofMinutes(5), cfg.value("dur", Duration.ofMinutes(5)).get());
        assertEquals(
                UUID.fromString("12345678-1234-1234-1234-123456789abc"),
                cfg.value("id", UUID.fromString("12345678-1234-1234-1234-123456789abc"))
                        .get());
        assertEquals(ZoneId.of("UTC"), cfg.value("zone", ZoneId.of("UTC")).get());
        assertEquals(
                new BigDecimal("1.25"), cfg.value("big", new BigDecimal("1.25")).get());
        assertEquals(
                URI.create("https://example.com"),
                cfg.value("uri", URI.create("https://example.com")).get());
        assertEquals(
                Pattern.compile("a+").pattern(),
                cfg.value("regex", Pattern.compile("a+")).get().pattern());

        assertEquals(
                List.of(ZoneId.of("UTC"), ZoneId.of("Europe/Moscow")),
                cfg.value("zones", List.of(ZoneId.of("UTC"), ZoneId.of("Europe/Moscow")), ValueCodec.ZONE_ID)
                        .get());
    }
}
