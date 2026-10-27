package team.terrafirmagreg.konf;

import com.electronwill.nightconfig.core.io.ParsingMode;
import com.electronwill.nightconfig.core.io.WritingMode;
import java.lang.ref.Cleaner;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.val;
import team.terrafirmagreg.konf.format.ConfigFormat;
import team.terrafirmagreg.konf.value.ValueBuilder;

@Setter
@Accessors(fluent = true, chain = true)
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class ConfigFileBuilder {

    private static final Cleaner CLEANER = Cleaner.create();

    private final Path file;

    @Setter(AccessLevel.NONE)
    private boolean autoreload;

    @Setter(AccessLevel.NONE)
    private boolean strict;

    private ConfigFormat format;
    private String defaultResource;
    private Path defaultData;
    private Charset charset;
    private WritingMode writingMode;
    private ParsingMode parsingMode;

    public ConfigFileBuilder autoreload() {
        this.autoreload = true;
        return this;
    }

    public ConfigFileBuilder strict() {
        this.strict = true;
        return this;
    }

    public ConfigFile build() {
        val resolved = format != null ? format : ConfigFormat.detect(file);
        val pending = new ArrayList<ValueBuilder<?>>();
        var builder = resolved.builder(file).preserveInsertionOrder().sync();
        if (autoreload) {
            builder = builder.autoreload().onAutoReload(() -> pending.forEach(ValueBuilder::fireChange));
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
        val configFile = new ConfigFile(file, resolved, fileConfig, strict, pending);
        if (autoreload) {
            CLEANER.register(configFile, fileConfig::close);
        }
        return configFile;
    }

    public ConfigFile open() {
        val config = build();
        config.load();
        return config;
    }
}
