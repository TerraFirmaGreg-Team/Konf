package team.terrafirmagreg.konf;

import com.electronwill.nightconfig.core.Config;
import java.nio.file.Path;
import java.util.ArrayList;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.konf.format.ConfigFormat;

@UtilityClass
public class Configs {

    static {
        Config.setInsertionOrderPreserved(true);
    }

    public ConfigFile toml(Path file) {
        return open(file, ConfigFormat.TOML);
    }

    public ConfigFile toml(String file) {
        return toml(Path.of(file));
    }

    public ConfigFile json(Path file) {
        return open(file, ConfigFormat.JSON);
    }

    public ConfigFile json(String file) {
        return json(Path.of(file));
    }

    public ConfigFile hocon(Path file) {
        return open(file, ConfigFormat.HOCON);
    }

    public ConfigFile hocon(String file) {
        return hocon(Path.of(file));
    }

    public ConfigFile yaml(Path file) {
        return open(file, ConfigFormat.YAML);
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

    private ConfigFile open(Path file, ConfigFormat format) {
        return builder(file).format(format).open();
    }
}
