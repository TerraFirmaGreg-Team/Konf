package team.terrafirmagreg.konf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Cleanup;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HooksTest {

    @TempDir
    Path dir;

    @Test
    void hooksFireOnSaveAndLoad() {
        val file = dir.resolve("hooked.toml");
        val saves = new AtomicInteger();
        val loads = new AtomicInteger();
        @Cleanup
        val cfg = Configs.builder(file)
                .onSave(p -> saves.incrementAndGet())
                .onLoad(p -> loads.incrementAndGet())
                .open();

        assertEquals(1, loads.get());
        cfg.save();
        cfg.value("x", 0).set(1);
        assertTrue(saves.get() >= 2, "explicit save + set-write must both fire");
        cfg.load();
        assertEquals(2, loads.get());
    }
}
