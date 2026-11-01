package team.terrafirmagreg.konf;

import com.electronwill.nightconfig.core.Config;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.val;
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
        return new ConfigFile(null, format, format.inMemory(), false, null, null);
    }

    public ConfigFile memory(String format) {
        return memory(Formats.byName(format));
    }

    public Map<String, String> env(String prefix) {
        return flatten(System.getenv(), prefix);
    }

    static Map<String, String> flatten(Map<String, String> vars, String prefix) {
        val out = new LinkedHashMap<String, String>();
        for (val e : vars.entrySet()) {
            val name = e.getKey();
            if (name.regionMatches(true, 0, prefix, 0, prefix.length())) {
                val key = name.substring(prefix.length())
                        .toLowerCase(Locale.ROOT)
                        .replaceAll("_+", ".")
                        .replaceAll("^\\.+|\\.+$", "");
                if (!key.isEmpty()) {
                    out.put(key, e.getValue());
                }
            }
        }
        return out;
    }

    private ConfigFile open(Path file, ConfigFormat format) {
        return builder(file).format(format).open();
    }
}
