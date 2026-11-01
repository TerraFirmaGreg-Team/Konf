package team.terrafirmagreg.konf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Cleanup;
import lombok.val;
import org.junit.jupiter.api.Test;
import team.terrafirmagreg.konf.format.TestFormat;
import team.terrafirmagreg.konf.value.ValueCodec;

class ConfigsTest {

    @Test
    void envFlattensVarsToDottedPaths() {
        val vars = new LinkedHashMap<String, String>();
        vars.put("APP_SERVER_PORT", "8080");
        vars.put("APP_SERVER__HOST", "0.0.0.0");
        vars.put("OTHER_X", "no");
        val flat = Configs.flatten(vars, "APP_");
        assertEquals(Map.of("server.port", "8080", "server.host", "0.0.0.0"), flat);
    }

    @Test
    void envPrefixMatchIsCaseInsensitive() {
        val flat = Configs.flatten(Map.of("app_port", "1"), "APP_");
        assertEquals(Map.of("port", "1"), flat);
    }

    @Test
    void mapOfFailsOnUndecodableElement() {
        val codec = ValueCodec.mapOf(ValueCodec.INTEGER);
        assertEquals(Map.of("a", 1), codec.load(Map.of("a", 1)));
        assertNull(codec.load(Map.of("a", "nope")));
    }

    @Test
    void overlayAppliesParsedScalars() {
        @Cleanup val cfg = Configs.memory(new TestFormat());
        cfg.overlay(Map.of(
                "server.port", "8080",
                "server.debug", "true",
                "server.ratio", "1.5",
                "server.name", "srv"));
        assertEquals(8080, cfg.value("server.port", 1).get());
        assertTrue(cfg.value("server.debug", false).get());
        assertEquals(1.5, cfg.value("server.ratio", 0.0).get());
        assertEquals("srv", cfg.value("server.name", "x").get());
    }

    @Test
    void overlaySurvivesLoad() {
        @Cleanup val cfg = Configs.memory(new TestFormat());
        cfg.overlay(Map.of("port", "8080"));
        cfg.load();
        assertEquals(8080, cfg.value("port", 1).get());
    }

    @Test
    void overlayKeysPassVerify() {
        @Cleanup val cfg = Configs.memory(new TestFormat());
        cfg.overlay(Map.of("injected.port", "8080"));
        cfg.value("declared", 1).get();
        cfg.verify();
    }
}
