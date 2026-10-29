package team.terrafirmagreg.konf.format;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.file.FileConfig;
import com.electronwill.nightconfig.core.file.GenericBuilder;
import java.nio.file.Path;
import java.util.List;

public class TestFormat implements ConfigFormat {

    @Override
    public String id() {
        return "test";
    }

    @Override
    public boolean commented() {
        return false;
    }

    @Override
    public List<String> extensions() {
        return List.of("testext", "tst");
    }

    @Override
    public GenericBuilder<?, ? extends FileConfig> builder(Path file) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Config inMemory() {
        return Config.inMemory();
    }
}
