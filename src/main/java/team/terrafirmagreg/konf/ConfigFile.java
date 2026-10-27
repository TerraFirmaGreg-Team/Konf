package team.terrafirmagreg.konf;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.file.FileConfig;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.val;
import org.jspecify.annotations.Nullable;
import team.terrafirmagreg.konf.format.ConfigFormat;
import team.terrafirmagreg.konf.value.ValueBuilder;
import team.terrafirmagreg.konf.value.ValueBuilder.Integral;
import team.terrafirmagreg.konf.value.ValueBuilder.Real;
import team.terrafirmagreg.konf.value.ValueCodec;
import team.terrafirmagreg.konf.value.Values;
import team.terrafirmagreg.konf.value.Values.Backend;

@Getter
@Accessors(fluent = true)
public final class ConfigFile {

    private final @Nullable Path path;
    private final ConfigFormat format;
    private final Config config;
    private final boolean strict;

    @Getter(AccessLevel.NONE)
    private final Backend backend;

    @Getter(AccessLevel.NONE)
    private final String prefix;

    @Getter(AccessLevel.NONE)
    private final List<ValueBuilder<?>> pending;

    @Getter(AccessLevel.NONE)
    private final Set<String> declared;

    @Getter(AccessLevel.NONE)
    private final Set<String> open;

    ConfigFile(@Nullable Path path, ConfigFormat format, Config config, boolean strict, List<ValueBuilder<?>> pending) {
        this(path, format, config, strict, "", pending, new HashSet<>(), new HashSet<>());
    }

    private ConfigFile(
            Path path,
            ConfigFormat format,
            Config config,
            boolean strict,
            String prefix,
            List<ValueBuilder<?>> pending,
            Set<String> declared,
            Set<String> open) {
        this.path = path;
        this.format = format;
        this.config = config;
        this.strict = strict;
        this.prefix = prefix;
        this.pending = pending;
        this.declared = declared;
        this.open = open;
        this.backend = Backend.of(this);
    }

    public ConfigFile at(String prefix) {
        return new ConfigFile(path, format, config, strict, resolve(prefix), pending, declared, open);
    }

    private String resolve(String path) {
        return path.isEmpty() || prefix.isEmpty() ? prefix + path : prefix + "." + path;
    }

    private String name() {
        return backend.name();
    }

    public void load() {
        ifFile(FileConfig::load);
    }

    public void save() {
        ifFile(FileConfig::save);
    }

    public void reload() {
        load();
        fireChanges();
    }

    public void sync() {
        pending.forEach(b -> b.entry().get());
        save();
    }

    public void reconcile(UnmodifiableConfig defaults) {
        Values.reconcile(config, defaults);
    }

    void fireChanges() {
        new ArrayList<>(pending).forEach(ValueBuilder::fireChange);
    }

    public void close() {
        ifFile(FileConfig::close);
    }

    private void ifFile(Consumer<FileConfig> op) {
        if (config instanceof FileConfig file) {
            op.accept(file);
        }
    }

    public void verify() {
        val declaredPaths = new HashSet<>(declared);
        pending.forEach(b -> declaredPaths.add(b.path()));
        val unknown = new ArrayList<String>();
        collectUnknown(config, "", declaredPaths, unknown);
        if (!unknown.isEmpty()) {
            throw InvalidConfigException.unknown(unknown, name());
        }
    }

    private void collectUnknown(
            UnmodifiableConfig node, String prefix, Set<String> declaredPaths, List<String> unknown) {
        for (val e : node.entrySet()) {
            val key = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            if (isOpen(key)) {
                continue;
            }
            if (e.getValue() instanceof UnmodifiableConfig sub) {
                collectUnknown(sub, key, declaredPaths, unknown);
            } else if (!declaredPaths.contains(key)) {
                unknown.add(key);
            }
        }
    }

    private boolean isOpen(String key) {
        for (val p : open) {
            if (key.equals(p) || key.startsWith(p + ".")) {
                return true;
            }
        }
        return false;
    }

    public Config subconfig(String path) {
        val resolved = resolve(path);
        open.add(resolved);
        Config sub = config.get(resolved);
        if (sub == null) {
            sub = config.createSubConfig();
            config.set(resolved, sub);
        }
        return sub;
    }

    public <T> T require(String path) {
        val resolved = resolve(path);
        declared.add(resolved);
        T value = config.get(resolved);
        if (value == null) {
            throw InvalidConfigException.missing(resolved, name());
        }
        return value;
    }

    public <T> T get(String path) {
        return config.get(resolve(path));
    }

    public void set(String path, Object value) {
        config.set(resolve(path), value);
    }

    public boolean contains(String path) {
        return config.contains(resolve(path));
    }

    public void comment(String path, String... lines) {
        val resolved = resolve(path);
        if (config instanceof CommentedConfig commented && lines.length > 0 && commented.getComment(resolved) == null) {
            commented.setComment(resolved, Values.indentComment(String.join("\n", lines)));
        }
    }

    public ValueBuilder<Boolean> value(String path, boolean def) {
        return track(new ValueBuilder<>(Values::bool, backend, resolve(path), def));
    }

    public ValueBuilder<Integer> value(String path, int def) {
        return track(new ValueBuilder<>(Values::integer, backend, resolve(path), def));
    }

    public Integral value(String path, long def) {
        return track(new Integral(Values::longValue, backend, resolve(path), def));
    }

    public Real value(String path, double def) {
        return track(new Real(Values::real, backend, resolve(path), def));
    }

    public ValueBuilder<Float> value(String path, float def) {
        return track(new ValueBuilder<>(Values::floatValue, backend, resolve(path), def));
    }

    public ValueBuilder<Byte> value(String path, byte def) {
        return track(new ValueBuilder<>(Values::byteValue, backend, resolve(path), def));
    }

    public ValueBuilder<Short> value(String path, short def) {
        return track(new ValueBuilder<>(Values::shortValue, backend, resolve(path), def));
    }

    public ValueBuilder<String> value(String path, String def) {
        return track(new ValueBuilder<>(Values::string, backend, resolve(path), def));
    }

    public ValueBuilder<BigInteger> value(String path, BigInteger def) {
        return value(path, def, ValueCodec.BIG_INTEGER);
    }

    public ValueBuilder<BigDecimal> value(String path, BigDecimal def) {
        return value(path, def, ValueCodec.BIG_DECIMAL);
    }

    public ValueBuilder<Duration> value(String path, Duration def) {
        return value(path, def, ValueCodec.DURATION);
    }

    public ValueBuilder<Period> value(String path, Period def) {
        return value(path, def, ValueCodec.PERIOD);
    }

    public ValueBuilder<Instant> value(String path, Instant def) {
        return value(path, def, ValueCodec.INSTANT);
    }

    public ValueBuilder<LocalDate> value(String path, LocalDate def) {
        return value(path, def, ValueCodec.LOCAL_DATE);
    }

    public ValueBuilder<LocalTime> value(String path, LocalTime def) {
        return value(path, def, ValueCodec.LOCAL_TIME);
    }

    public ValueBuilder<LocalDateTime> value(String path, LocalDateTime def) {
        return value(path, def, ValueCodec.LOCAL_DATE_TIME);
    }

    public ValueBuilder<OffsetDateTime> value(String path, OffsetDateTime def) {
        return value(path, def, ValueCodec.OFFSET_DATE_TIME);
    }

    public ValueBuilder<ZoneId> value(String path, ZoneId def) {
        return value(path, def, ValueCodec.ZONE_ID);
    }

    public ValueBuilder<UUID> value(String path, UUID def) {
        return value(path, def, ValueCodec.UUID_CODEC);
    }

    public ValueBuilder<URI> value(String path, URI def) {
        return value(path, def, ValueCodec.URI_CODEC);
    }

    public ValueBuilder<Path> value(String path, Path def) {
        return value(path, def, ValueCodec.PATH);
    }

    public ValueBuilder<Pattern> value(String path, Pattern def) {
        return value(path, def, ValueCodec.PATTERN);
    }

    public ValueBuilder<Charset> value(String path, Charset def) {
        return value(path, def, ValueCodec.CHARSET);
    }

    public ValueBuilder<Locale> value(String path, Locale def) {
        return value(path, def, ValueCodec.LOCALE);
    }

    public ValueBuilder<List<String>> value(String path, List<String> def) {
        return track(new ValueBuilder<>(Values::stringList, backend, resolve(path), def));
    }

    public <E extends Enum<E>> ValueBuilder<E> value(String path, E def) {
        return track(new ValueBuilder<>(Values::enumValue, backend, resolve(path), def));
    }

    public <T> ValueBuilder<T> value(String path, T def, ValueCodec<T> codec) {
        return track(new ValueBuilder<>((bk, b) -> Values.coded(bk, b, codec), backend, resolve(path), def));
    }

    public <T> ValueBuilder<List<T>> value(String path, List<T> def, ValueCodec<T> elementCodec) {
        return track(new ValueBuilder<>((bk, b) -> Values.typedList(bk, b, elementCodec), backend, resolve(path), def));
    }

    public <T> ValueBuilder<T> value(String path, T def) {
        return track(new ValueBuilder<>(Values::generic, backend, resolve(path), def));
    }

    private <B extends ValueBuilder<?>> B track(B builder) {
        pending.add(builder);
        return builder;
    }
}
