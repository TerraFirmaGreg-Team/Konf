package team.terrafirmagreg.konf.format;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.file.FileConfig;
import com.electronwill.nightconfig.core.file.GenericBuilder;
import com.electronwill.nightconfig.hocon.HoconFormat;
import com.electronwill.nightconfig.json.JsonFormat;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.yaml.YamlFormat;
import java.nio.file.Path;
import java.util.Locale;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.val;
import team.terrafirmagreg.konf.internal.SpacedFormat;

/**
 * Supported file formats. {@code commented} marks formats whose writer actually renders comments (night-config writes
 * them only for TOML and HOCON).
 */
@Getter
@Accessors(fluent = true)
public enum ConfigFormat {
    TOML(true, "toml"),
    JSON(false, "json"),
    HOCON(true, "conf", "hocon"),
    YAML(false, "yaml", "yml");

    private final boolean commented;
    private final String extension;
    private final String[] aliases;

    ConfigFormat(boolean commented, String extension, String... aliases) {
        this.commented = commented;
        this.extension = extension;
        this.aliases = aliases;
    }

    public GenericBuilder<?, ? extends FileConfig> builder(Path file) {
        return switch (this) {
            case TOML -> CommentedFileConfig.builder(file, new SpacedFormat<>(TomlFormat.instance()));
            case HOCON -> CommentedFileConfig.builder(file, new SpacedFormat<>(HoconFormat.instance()));
            case JSON -> FileConfig.builder(file, JsonFormat.fancyInstance());
            case YAML -> FileConfig.builder(file, YamlFormat.defaultInstance());
        };
    }

    public Config inMemory() {
        return switch (this) {
            case TOML -> CommentedConfig.of(new SpacedFormat<>(TomlFormat.instance()));
            case HOCON -> CommentedConfig.of(new SpacedFormat<>(HoconFormat.instance()));
            case JSON -> Config.of(JsonFormat.fancyInstance());
            case YAML -> Config.of(YamlFormat.defaultInstance());
        };
    }

    public static ConfigFormat detect(Path file) {
        val name = file.getFileName().toString();
        val dot = name.lastIndexOf('.');
        val ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        for (val format : values()) {
            if (format.extension.equals(ext)) {
                return format;
            }
            for (val alias : format.aliases) {
                if (alias.equals(ext)) {
                    return format;
                }
            }
        }
        throw new IllegalArgumentException("Unknown config file extension: " + name);
    }
}
