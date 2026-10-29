package team.terrafirmagreg.konf;

import com.electronwill.nightconfig.core.Config;
import java.nio.file.Path;
import java.util.ArrayList;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.konf.format.ConfigFormat;
import team.terrafirmagreg.konf.format.Formats;

@UtilityClass
public class Configs {

    static {
        Config.setInsertionOrderPreserved(true);
    }

    public ConfigFile toml(Path file) {
        return open(file, Formats.byName("toml"));
    }

    public ConfigFile toml(String file) {
        return toml(Path.of(file));
    }

    public ConfigFile json(Path file) {
        return open(file, Formats.byName("json"));
    }

    public ConfigFile json(String file) {
        return json(Path.of(file));
    }

    public ConfigFile hocon(Path file) {
        return open(file, Formats.byName("hocon"));
    }

    public ConfigFile hocon(String file) {
        return hocon(Path.of(file));
    }

    public ConfigFile yaml(Path file) {
        return open(file, Formats.byName("yaml"));
    }

    public ConfigFile yaml(String file) {
        return yaml(Path.of(file));
    }

    public ConfigFile of(Path file) {
        return builder(file).open();
    }

    public ConfigFile of(String file) {
        return of(Path.of(file));
    }

    public ConfigFileBuilder builder(Path file) {
        return new ConfigFileBuilder(file);
    }

    public ConfigFileBuilder builder(String file) {
        return builder(Path.of(file));
    }

    public ConfigFile memory(ConfigFormat format) {
        return new ConfigFile(null, format, format.inMemory(), false, new ArrayList<>());
    }

    public ConfigFile memory(String format) {
        return memory(Formats.byName(format));
    }

    private ConfigFile open(Path file, ConfigFormat format) {
        return builder(file).format(format).open();
    }
}
