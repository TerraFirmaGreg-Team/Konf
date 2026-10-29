package team.terrafirmagreg.konf.json;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.file.FileConfig;
import com.electronwill.nightconfig.core.file.GenericBuilder;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import team.terrafirmagreg.konf.format.ConfigFormat;

public final class JsonFormat implements ConfigFormat {

    public static final JsonFormat INSTANCE = new JsonFormat();

    public JsonFormat() {}

    @Override
    public String id() {
        return "json";
    }

    @Override
    public boolean commented() {
        return false;
    }

    @Override
    public List<String> extensions() {
        return List.of("json");
    }

    @Override
    public GenericBuilder<?, ? extends FileConfig> builder(Path file) {
        return FileConfig.builder(file, com.electronwill.nightconfig.json.JsonFormat.fancyInstance());
    }

    @Override
    public Config inMemory() {
        return Config.of(LinkedHashMap::new, com.electronwill.nightconfig.json.JsonFormat.fancyInstance());
    }
}
