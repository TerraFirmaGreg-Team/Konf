package team.terrafirmagreg.konf.value;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.regex.Pattern;
import lombok.Cleanup;
import lombok.val;
import org.junit.jupiter.api.Test;
import team.terrafirmagreg.konf.ConfigFile;
import team.terrafirmagreg.konf.Configs;
import team.terrafirmagreg.konf.format.TestFormat;

class ConfigValueTest {

    private static ConfigFile file() {
        return Configs.memory(new TestFormat());
    }

    @Test
    void exposesPathAndDefault() {
        @Cleanup val cfg = file();
        val v = new ConfigValue<>(cfg, ValueCodec.INTEGER, "server.port", 25565);
        assertEquals("server.port", v.path());
        assertEquals(25565, v.defaultValue());
    }

    @Test
    void joinsCommentLines() {
        @Cleanup val cfg = file();
        val v = new ConfigValue<>(cfg, ValueCodec.INTEGER, "a", 1).comment("first", "second");
        assertEquals("first\nsecond", v.comment());
    }

    @Test
    void storesRangeBounds() {
        @Cleanup val cfg = file();
        val v = new ConfigValue<>(cfg, ValueCodec.INTEGER, "a", 5).range(1, 10);
        assertEquals(1, v.min());
        assertEquals(10, v.max());
    }

    @Test
    void rangeRejectsNonComparableBounds() {
        @Cleanup val cfg = file();
        val v = new ConfigValue<>(cfg, ValueCodec.PATTERN, "re", Pattern.compile("x"));
        assertThrows(IllegalArgumentException.class, () -> v.range(Pattern.compile("a"), Pattern.compile("b")));
    }

    @Test
    void getMaterializesMissingKey() {
        @Cleanup val cfg = file();
        val v = new ConfigValue<>(cfg, ValueCodec.INTEGER, "k", 5);
        assertEquals(5, v.get());
        assertEquals(5, cfg.<Integer>get("k"));
    }

    @Test
    void setWritesThroughImmediately() {
        @Cleanup val cfg = file();
        val v = new ConfigValue<>(cfg, ValueCodec.INTEGER, "k", 5);
        v.set(9);
        assertEquals(9, v.get());
        assertEquals(9, cfg.<Integer>get("k"));
    }

    @Test
    void validateRejectsOnSet() {
        @Cleanup val cfg = file();
        val v = new ConfigValue<>(cfg, ValueCodec.INTEGER, "k", 5).validate(x -> x > 0);
        assertThrows(IllegalArgumentException.class, () -> v.set(-1));
    }

    @Test
    void invalidFileValueResetsToDefault() {
        @Cleanup val cfg = file();
        cfg.set("k", "nope");
        val v = new ConfigValue<>(cfg, ValueCodec.INTEGER, "k", 5);
        assertEquals(5, v.get());
        assertEquals(5, cfg.<Integer>get("k"));
    }

    @Test
    void outOfRangeClampsAndWritesBack() {
        @Cleanup val cfg = file();
        cfg.set("k", 99L);
        val v = new ConfigValue<>(cfg, ValueCodec.INTEGER, "k", 5).range(1, 10);
        assertEquals(10, v.get());
        assertEquals(10, cfg.<Integer>get("k"));
    }

    @Test
    void nullSetRejected() {
        @Cleanup val cfg = file();
        val v = new ConfigValue<>(cfg, ValueCodec.INTEGER, "k", 5);
        assertThrows(NullPointerException.class, () -> v.set(null));
    }

    @Test
    void nullDefaultRejectedAtConstruction() {
        @Cleanup val cfg = file();
        assertThrows(NullPointerException.class, () -> new ConfigValue<>(cfg, ValueCodec.STRING, "s", null));
    }
}
