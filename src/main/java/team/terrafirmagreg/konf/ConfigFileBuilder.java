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

/**
 * Builder for {@link ConfigFile}; without an explicit {@link #format} it is detected from the file extension. Writes
 * are synchronous; {@link #autoreload} watches the file and re-reads it on external changes, firing declared values'
 * {@code onChange} listeners.
 */
@Setter
@Accessors(fluent = true, chain = true)
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class ConfigFileBuilder {

    private static final Cleaner CLEANER = Cleaner.create();

    private final Path file;

    @Setter(AccessLevel.NONE)
    private boolean autoreload;

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

    public ConfigFile build() {
        val resolved = format != null ? format : ConfigFormat.detect(file);
        // the watch handler must not retain the ConfigFile wrapper — it iterates the shared pending list directly
        val pending = new ArrayList<ValueBuilder<?>>();
        var builder = resolved.builder(file).sync();
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
        val configFile = new ConfigFile(file, resolved, fileConfig, pending);
        if (autoreload) {
            // watcher holds fileConfig, not the wrapper — release the watch once the wrapper is GC'd
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
