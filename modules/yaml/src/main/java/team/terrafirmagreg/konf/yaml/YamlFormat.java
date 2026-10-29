package team.terrafirmagreg.konf.yaml;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.file.FileConfig;
import com.electronwill.nightconfig.core.file.GenericBuilder;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import team.terrafirmagreg.konf.format.ConfigFormat;

public final class YamlFormat implements ConfigFormat {

    public static final YamlFormat INSTANCE = new YamlFormat();

    public YamlFormat() {}

    @Override
    public String id() {
        return "yaml";
    }

    @Override
    public boolean commented() {
        return false;
    }

    @Override
    public List<String> extensions() {
        return List.of("yaml", "yml");
    }

    @Override
    public GenericBuilder<?, ? extends FileConfig> builder(Path file) {
        return FileConfig.builder(file, com.electronwill.nightconfig.yaml.YamlFormat.defaultInstance());
    }

    @Override
    public Config inMemory() {
        return Config.of(LinkedHashMap::new, com.electronwill.nightconfig.yaml.YamlFormat.defaultInstance());
    }
}
