package team.terrafirmagreg.konf;

import com.electronwill.nightconfig.core.io.ParsingMode;
import com.electronwill.nightconfig.core.io.WritingMode;
import java.lang.ref.Cleaner;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;
import lombok.val;
import team.terrafirmagreg.konf.format.ConfigFormat;
import team.terrafirmagreg.konf.format.Formats;

@Setter
@Accessors(fluent = true, chain = true)
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@FieldDefaults(level = AccessLevel.PRIVATE)
public final class ConfigFileBuilder {

    private static final Cleaner CLEANER = Cleaner.create();

    final Path file;

    @Setter(AccessLevel.NONE)
    boolean autoreload;

    @Setter(AccessLevel.NONE)
    boolean strict;

    Consumer<Path> onSave;

    Consumer<Path> onLoad;

    ConfigFormat format;

    public ConfigFileBuilder format(ConfigFormat format) {
        this.format = format;
        return this;
    }

    public ConfigFileBuilder format(String name) {
        return format(Formats.byName(name));
    }

    String defaultResource;
    Path defaultData;
    Charset charset;
    WritingMode writingMode;
    ParsingMode parsingMode;

    public ConfigFileBuilder autoreload() {
        this.autoreload = true;
        return this;
    }

    public ConfigFileBuilder strict() {
        this.strict = true;
        return this;
    }

    public ConfigFile build() {
        val resolved = format != null ? format : Formats.detect(file);
        val ref = new AtomicReference<ConfigFile>();
        var builder = resolved.builder(file).preserveInsertionOrder().sync();
        if (autoreload) {
            builder = builder.autoreload().onAutoReload(() -> {
                val cfg = ref.get();
                if (cfg != null) {
                    cfg.postLoad();
                }
            });
        }
        if (defaultResource != null) {
            builder = builder.defaultResource(defaultResource);
        }
        if (defaultData != null) {
            builder = builder.defaultData(defaultData);
        }
        if (charset != null) {
            builder = builder.charset(charset);
        }
        if (writingMode != null) {
            builder = builder.writingMode(writingMode);
        }
        if (parsingMode != null) {
            builder = builder.parsingMode(parsingMode);
        }
        val fileConfig = builder.build();
        val configFile = new ConfigFile(file, resolved, fileConfig, strict, onSave, onLoad);
        ref.set(configFile);
        configFile.load();
        if (autoreload) {
            CLEANER.register(configFile, fileConfig::close);
        }
        return configFile;
    }

    public ConfigFile open() {
        return build();
    }
}
