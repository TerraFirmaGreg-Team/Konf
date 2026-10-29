package team.terrafirmagreg.konf.toml;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.file.FileConfig;
import com.electronwill.nightconfig.core.file.GenericBuilder;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import team.terrafirmagreg.konf.format.ConfigFormat;
import team.terrafirmagreg.konf.internal.SpacedFormat;

public final class TomlFormat implements ConfigFormat {

    public static final TomlFormat INSTANCE = new TomlFormat();

    public TomlFormat() {}

    @Override
    public String id() {
        return "toml";
    }

    @Override
    public boolean commented() {
        return true;
    }

    @Override
    public List<String> extensions() {
        return List.of("toml");
    }

    @Override
    public GenericBuilder<?, ? extends FileConfig> builder(Path file) {
        return CommentedFileConfig.builder(
                file, new SpacedFormat<>(com.electronwill.nightconfig.toml.TomlFormat.instance()));
    }

    @Override
    public Config inMemory() {
        return CommentedConfig.of(
                LinkedHashMap::new, new SpacedFormat<>(com.electronwill.nightconfig.toml.TomlFormat.instance()));
    }
}
