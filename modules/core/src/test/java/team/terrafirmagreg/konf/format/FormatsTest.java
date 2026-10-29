package team.terrafirmagreg.konf.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import lombok.val;
import org.junit.jupiter.api.Test;

class FormatsTest {

    @Test
    void loadsFormatsFromServiceLoader() {
        assertInstanceOf(TestFormat.class, Formats.byName("test"));
        assertEquals(1, Formats.all().size());
    }

    @Test
    void detectsByExtensionAndAliases() {
        assertInstanceOf(TestFormat.class, Formats.detect(Path.of("a.testext")));
        assertInstanceOf(TestFormat.class, Formats.detect(Path.of("a.tst")));
        assertInstanceOf(TestFormat.class, Formats.detect(Path.of("A.TESTEXT")));
    }

    @Test
    void throwsOnUnknownExtension() {
        assertThrows(IllegalArgumentException.class, () -> Formats.detect(Path.of("a.ini")));
        assertThrows(IllegalArgumentException.class, () -> Formats.detect(Path.of("a")));
    }

    @Test
    void missingFormatNamesTheModule() {
        val e = assertThrows(IllegalStateException.class, () -> Formats.byName("toml"));
        assertTrue(e.getMessage().contains("konf-toml"), e.getMessage());
    }
}
