package team.terrafirmagreg.konf.value;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ValueBuilderTest {

    private static <T> ValueBuilder<T> builder(String path, T def) {
        return new ValueBuilder<>(b -> ConfigValue.of(b.defaultValue(), () -> b.defaultValue(), v -> {}), path, def);
    }

    @Test
    void exposesPathAndDefault() {
        var b = builder("server.port", 25565);
        assertEquals("server.port", b.path());
        assertEquals(25565, b.defaultValue());
    }

    @Test
    void joinsCommentLines() {
        var b = builder("a", 1).comment("first", "second");
        assertEquals("first\nsecond", b.comment());
    }

    @Test
    void storesRangeBounds() {
        var b = builder("a", 5).range(1, 10);
        assertEquals(1, b.min());
        assertEquals(10, b.max());
    }

    @Test
    void fireChangePushesCurrentValue() {
        var seen = new AtomicInteger();
        var b = builder("a", 7).onChange(seen::set);
        b.fireChange();
        assertEquals(7, seen.get());
    }

    @Test
    void realRangeAcceptsIntLiterals() {
        var b = new ValueBuilder.Real(x -> ConfigValue.of(x.defaultValue(), () -> x.defaultValue(), v -> {}), "a", 1.5);
        b.range(0, 10);
        assertEquals(0.0, b.min());
        assertEquals(10.0, b.max());
    }
}
