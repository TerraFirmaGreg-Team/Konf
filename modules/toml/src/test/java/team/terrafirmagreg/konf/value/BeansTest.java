package team.terrafirmagreg.konf.value;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.electronwill.nightconfig.core.serde.annotations.SerdeComment;
import com.electronwill.nightconfig.core.serde.annotations.SerdeKey;
import com.electronwill.nightconfig.core.serde.annotations.SerdeSkipSerializingIf;
import com.electronwill.nightconfig.core.serde.annotations.SerdeSkipSerializingIf.SkipSerIf;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Cleanup;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import team.terrafirmagreg.konf.Configs;

class BeansTest {

    @TempDir
    Path dir;

    enum Level {
        LOW,
        HIGH
    }

    public static class Inner {
        public int x = 1;
        public boolean flag = true;
    }

    public static class Outer {
        public String name = "n";
        public int port = 80;
        public byte level = 3;
        public short mask = 4;
        public Level mode = Level.LOW;
        public Inner inner = new Inner();
        public List<Inner> items = List.of(new Inner());
        public Map<String, Integer> caps = new LinkedHashMap<>(Map.of("a", 1));
        private int priv = 11;
        public transient String temp = "x";

        @SerdeKey("renamed_key")
        public int renamed = 5;

        @SerdeComment("doc comment from the bean")
        public int commented = 6;

        @SerdeSkipSerializingIf(SkipSerIf.IS_NULL)
        public String maybe;

        public int priv() {
            return priv;
        }
    }

    public record Point(int x, String label) {}

    public static class NoCtor {
        public NoCtor(int need) {}
    }

    @Test
    void beanRoundTripsAllFieldKinds() throws Exception {
        val file = dir.resolve("b.toml");
        @Cleanup val cfg = Configs.toml(file);
        cfg.value("app", new Outer(), ValueCodec.bean(Outer.class));
        cfg.sync();
        val text = Files.readString(file);
        assertTrue(text.contains("name = \"n\""), text);
        assertTrue(text.contains("port = 80"), text);
        assertTrue(text.contains("[app.inner]"), text);
        assertTrue(text.contains("[[app.items]]"), text);
        assertTrue(text.contains("mode = \"LOW\""), text);
        assertTrue(text.contains("[app.caps]"), text);
        assertTrue(text.contains("renamed_key = 5"), text);
        assertTrue(text.contains("doc comment from the bean"), text);
        assertTrue(text.contains("priv = 11"), text);
        assertFalse(text.contains("maybe"), text);
        assertFalse(text.contains("temp"), text);
        assertFalse(text.contains("renamed ="), text);
    }

    @Test
    void beanReadsBackFileValues() throws Exception {
        val file = dir.resolve("b.toml");
        Files.writeString(file, """
                [app]
                name = "srv"
                port = 1234
                level = 7
                mask = 9
                mode = "HIGH"
                maybe = "yes"
                renamed_key = 42
                priv = 77
                [app.inner]
                x = 42
                flag = false
                [[app.items]]
                x = 5
                flag = true
                [app.caps]
                a = 3
                b = 4
                """);
        @Cleanup val cfg = Configs.toml(file);
        val loaded = cfg.value("app", new Outer(), ValueCodec.bean(Outer.class)).get();
        assertEquals("srv", loaded.name);
        assertEquals(1234, loaded.port);
        assertEquals(7, loaded.level);
        assertEquals(9, loaded.mask);
        assertEquals(Level.HIGH, loaded.mode);
        assertEquals("yes", loaded.maybe);
        assertEquals(42, loaded.renamed);
        assertEquals(77, loaded.priv());
        assertEquals(42, loaded.inner.x);
        assertFalse(loaded.inner.flag);
        assertEquals(5, loaded.items.get(0).x);
        assertEquals(Map.of("a", 3, "b", 4), loaded.caps);
        assertEquals(6, loaded.commented);
    }

    @Test
    void beanToleratesMissingKeys() throws Exception {
        val file = dir.resolve("b.toml");
        Files.writeString(file, "[app]\nname = \"only\"\n");
        @Cleanup val cfg = Configs.toml(file);
        val loaded = cfg.value("app", new Outer(), ValueCodec.bean(Outer.class)).get();
        assertEquals("only", loaded.name);
        assertEquals(80, loaded.port);
        assertEquals(1, loaded.inner.x);
    }

    @Test
    void recordBeanRoundTrip() throws Exception {
        val file = dir.resolve("r.toml");
        @Cleanup val cfg = Configs.toml(file);
        val point = cfg.value("point", new Point(1, "a"), ValueCodec.bean(Point.class));
        cfg.sync();
        val text = Files.readString(file);
        assertTrue(text.contains("x = 1"), text);
        assertTrue(text.contains("label = \"a\""), text);
        Files.writeString(file, "point = {x = 9, label = \"z\"}\n");
        cfg.reload();
        assertEquals(new Point(9, "z"), point.get());
    }

    @Test
    void beanRejectsTypeWithoutNoArgCtor() {
        @Cleanup val cfg = Configs.toml(dir.resolve("n.toml"));
        assertThrows(
                IllegalArgumentException.class, () -> cfg.value("n", new NoCtor(1), ValueCodec.bean(NoCtor.class)));
    }

    @Test
    void beanListViaTypedList() throws Exception {
        val file = dir.resolve("b.toml");
        @Cleanup val cfg = Configs.toml(file);
        val items = cfg.value("items", List.of(new Inner()), ValueCodec.bean(Inner.class));
        cfg.sync();
        val text = Files.readString(file);
        assertTrue(text.contains("[[items]]"), text);
        val i = new Inner();
        i.x = 9;
        items.set(List.of(i));
        assertEquals(9, items.get().get(0).x);
    }

    @Test
    void beanDeclaresWholeSubtreeForVerify() {
        @Cleanup val cfg = Configs.toml(dir.resolve("v.toml"));
        cfg.value("app", new Outer(), ValueCodec.bean(Outer.class)).get();
        cfg.verify();
    }
}
