package team.terrafirmagreg.konf;

import java.nio.file.Path;
import java.util.ArrayList;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.konf.format.ConfigFormat;

@UtilityClass
public class Configs {

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

    /** Format detected from the file extension ({@code .toml .json .conf .hocon .yaml .yml}). */
    public ConfigFile of(Path file) {
        return builder(file).open();
    }

    public ConfigFile of(String file) {
        return of(Path.of(file));
    }

    /** Format detected from the extension unless overridden via {@link ConfigFileBuilder#format}. */
    public ConfigFileBuilder builder(Path file) {
        return new ConfigFileBuilder(file);
    }

    public ConfigFileBuilder builder(String file) {
        return builder(Path.of(file));
    }

    /** In-memory config of the given format — no file, {@code load}/{@code save}/{@code close} are no-ops. */
    public ConfigFile memory(ConfigFormat format) {
        return new ConfigFile(null, format, format.inMemory(), false, new ArrayList<>());
    }

    private ConfigFile open(Path file, ConfigFormat format) {
        return builder(file).format(format).open();
    }
}
