package team.terrafirmagreg.konf.value;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
        public String maybe;
    }

    @Test
    void beanWritesSnakeCaseTablesAndSkipsNulls() throws Exception {
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
        assertFalse(text.contains("maybe"), text);
    }

    @Test
    void beanReadsBackAllFieldKinds() throws Exception {
        val file = dir.resolve("b.toml");
        Files.writeString(file, """
                [app]
                name = "srv"
                port = 1234
                level = 7
                mask = 9
                mode = "HIGH"
                maybe = "yes"
                [app.inner]
                x = 42
                flag = false
                [[app.items]]
                x = 5
                flag = true
                """);
        @Cleanup val cfg = Configs.toml(file);
        val loaded = cfg.value("app", new Outer(), ValueCodec.bean(Outer.class)).get();
        assertEquals("srv", loaded.name);
        assertEquals(1234, loaded.port);
        assertEquals(7, loaded.level);
        assertEquals(9, loaded.mask);
        assertEquals(Level.HIGH, loaded.mode);
        assertEquals("yes", loaded.maybe);
        assertEquals(42, loaded.inner.x);
        assertFalse(loaded.inner.flag);
        assertEquals(5, loaded.items.get(0).x);
    }

    @Test
    void beanResetsOnMalformedField() throws Exception {
        val file = dir.resolve("b.toml");
        Files.writeString(file, "[app]\nport = \"abc\"\nname = \"srv\"\n");
        @Cleanup val cfg = Configs.toml(file);
        val loaded = cfg.value("app", new Outer(), ValueCodec.bean(Outer.class)).get();
        assertEquals(80, loaded.port);
        assertEquals("n", loaded.name);
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
}
