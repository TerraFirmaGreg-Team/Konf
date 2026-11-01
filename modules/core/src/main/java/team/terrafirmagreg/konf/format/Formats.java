package team.terrafirmagreg.konf.format;

import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import lombok.experimental.UtilityClass;
import lombok.val;

@UtilityClass
public class Formats {

    private static final Map<String, ConfigFormat> BY_NAME = new LinkedHashMap<>();
    private static final Map<String, ConfigFormat> BY_EXTENSION = new LinkedHashMap<>();

    static {
        for (val f : ServiceLoader.load(ConfigFormat.class)) {
            register(f);
        }
    }

    static void register(ConfigFormat format) {
        val prev = BY_NAME.get(format.id());
        if (prev != null) {
            throw new IllegalStateException("Duplicate config format id '" + format.id() + "': "
                    + prev.getClass().getName() + " vs " + format.getClass().getName());
        }
        for (val ext : format.extensions()) {
            val prevExt = BY_EXTENSION.get(ext);
            if (prevExt != null) {
                throw new IllegalStateException("Duplicate config format extension '" + ext + "': "
                        + prevExt.getClass().getName() + " vs "
                        + format.getClass().getName());
            }
        }
        BY_NAME.put(format.id(), format);
        for (val ext : format.extensions()) {
            BY_EXTENSION.put(ext, format);
        }
    }

    public static ConfigFormat byName(String name) {
        val format = BY_NAME.get(name);
        if (format == null) {
            throw new IllegalStateException(
                    "No config format '" + name + "' on the classpath — add the konf-" + name + " dependency");
        }
        return format;
    }

    public static ConfigFormat detect(Path file) {
        val name = file.getFileName().toString();
        val dot = name.lastIndexOf('.');
        val ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        val format = BY_EXTENSION.get(ext);
        if (format == null) {
            throw new IllegalArgumentException("Unknown config file extension: " + name);
        }
        return format;
    }

    public static Collection<ConfigFormat> all() {
        return List.copyOf(BY_NAME.values());
    }
}
