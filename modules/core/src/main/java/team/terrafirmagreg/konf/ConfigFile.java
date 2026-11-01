package team.terrafirmagreg.konf;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.NullObject;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;
import lombok.val;
import org.jspecify.annotations.Nullable;
import team.terrafirmagreg.konf.format.ConfigFormat;
import team.terrafirmagreg.konf.value.ConfigValue;
import team.terrafirmagreg.konf.value.ValueCodec;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class ConfigFile implements AutoCloseable {

    private static final Pattern COMMENT_INDENT = Pattern.compile("(?m)^(\\S)");

    @Nullable
    Path path;

    @NonNull
    ConfigFormat format;

    @NonNull
    Config config;

    boolean strict;

    @Nullable
    Consumer<Path> onSave;

    @Nullable
    Consumer<Path> onLoad;

    @Getter(AccessLevel.NONE)
    List<ConfigValue<?>> pending = new ArrayList<>();

    @Getter(AccessLevel.NONE)
    List<Map<String, Object>> overlays = new ArrayList<>();

    public void load() {
        ifFile(FileConfig::load);
        postLoad();
    }

    void postLoad() {
        overlays.forEach(o -> o.forEach(config::set));
        if (onLoad != null && path != null) {
            onLoad.accept(path);
        }
    }

    public void save() {
        ifFile(FileConfig::save);
        if (onSave != null && path != null) {
            onSave.accept(path);
        }
    }

    public void sync() {
        pending.forEach(ConfigValue::get);
        save();
    }

    @Override
    public void close() {
        ifFile(FileConfig::close);
    }

    private void ifFile(Consumer<FileConfig> op) {
        if (config instanceof FileConfig file) {
            op.accept(file);
        }
    }

    private String name() {
        return path != null ? path.getFileName().toString() : "<memory>";
    }

    public void verify() {
        val declaredPaths = new HashSet<String>();
        pending.forEach(v -> declaredPaths.add(v.path()));
        overlays.forEach(o -> declaredPaths.addAll(o.keySet()));
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
            if (declaredPaths.contains(key)) {
                continue;
            }
            if (e.getValue() instanceof UnmodifiableConfig sub) {
                collectUnknown(sub, key, declaredPaths, unknown);
            } else {
                unknown.add(key);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <T> @Nullable T get(String path) {
        Object value = config.get(path);
        return value instanceof NullObject ? null : (T) value;
    }

    public void set(String path, Object value) {
        config.set(path, value);
    }

    public void remove(String path) {
        config.remove(path);
    }

    public boolean contains(String path) {
        return config.contains(path);
    }

    public void comment(String path, String... lines) {
        if (config instanceof CommentedConfig commented && lines.length > 0 && commented.getComment(path) == null) {
            commented.setComment(path, indentComment(String.join("\n", lines)));
        }
    }

    private static String indentComment(String comment) {
        return COMMENT_INDENT.matcher(comment).replaceAll(" $1");
    }

    public void overlayEnv(String prefix) {
        overlay(Configs.env(prefix));
    }

    void overlay(Map<String, ?> values) {
        val resolved = new LinkedHashMap<String, Object>();
        values.forEach((k, v) -> resolved.put(k, scalar(v)));
        overlays.add(resolved);
        resolved.forEach(config::set);
    }

    private static Object scalar(Object value) {
        if (!(value instanceof String s)) {
            return value;
        }
        if (s.equalsIgnoreCase("true") || s.equalsIgnoreCase("false")) {
            return Boolean.parseBoolean(s);
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException ignored) {
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException ignored) {
        }
        return s;
    }

    public ConfigValue<Boolean> value(String path, boolean def) {
        return track(new ConfigValue<>(this, ValueCodec.BOOLEAN, path, def));
    }

    public ConfigValue<Integer> value(String path, int def) {
        return track(new ConfigValue<>(this, ValueCodec.INTEGER, path, def));
    }

    public ConfigValue<Long> value(String path, long def) {
        return track(new ConfigValue<>(this, ValueCodec.LONG, path, def));
    }

    public ConfigValue<Double> value(String path, double def) {
        return track(new ConfigValue<>(this, ValueCodec.DOUBLE, path, def));
    }

    public ConfigValue<Float> value(String path, float def) {
        return track(new ConfigValue<>(this, ValueCodec.FLOAT, path, def));
    }

    public ConfigValue<Byte> value(String path, byte def) {
        return track(new ConfigValue<>(this, ValueCodec.BYTE, path, def));
    }

    public ConfigValue<Short> value(String path, short def) {
        return track(new ConfigValue<>(this, ValueCodec.SHORT, path, def));
    }

    public ConfigValue<String> value(String path, String def) {
        return track(new ConfigValue<>(this, ValueCodec.STRING, path, def));
    }

    public ConfigValue<BigInteger> value(String path, BigInteger def) {
        return track(new ConfigValue<>(this, ValueCodec.BIG_INTEGER, path, def));
    }

    public ConfigValue<BigDecimal> value(String path, BigDecimal def) {
        return track(new ConfigValue<>(this, ValueCodec.BIG_DECIMAL, path, def));
    }

    public ConfigValue<Duration> value(String path, Duration def) {
        return track(new ConfigValue<>(this, ValueCodec.DURATION, path, def));
    }

    public ConfigValue<Period> value(String path, Period def) {
        return track(new ConfigValue<>(this, ValueCodec.PERIOD, path, def));
    }

    public ConfigValue<Instant> value(String path, Instant def) {
        return track(new ConfigValue<>(this, ValueCodec.INSTANT, path, def));
    }

    public ConfigValue<LocalDate> value(String path, LocalDate def) {
        return track(new ConfigValue<>(this, ValueCodec.LOCAL_DATE, path, def));
    }

    public ConfigValue<LocalTime> value(String path, LocalTime def) {
        return track(new ConfigValue<>(this, ValueCodec.LOCAL_TIME, path, def));
    }

    public ConfigValue<LocalDateTime> value(String path, LocalDateTime def) {
        return track(new ConfigValue<>(this, ValueCodec.LOCAL_DATE_TIME, path, def));
    }

    public ConfigValue<OffsetDateTime> value(String path, OffsetDateTime def) {
        return track(new ConfigValue<>(this, ValueCodec.OFFSET_DATE_TIME, path, def));
    }

    public ConfigValue<ZoneId> value(String path, ZoneId def) {
        return track(new ConfigValue<>(this, ValueCodec.ZONE_ID, path, def));
    }

    public ConfigValue<UUID> value(String path, UUID def) {
        return track(new ConfigValue<>(this, ValueCodec.UUID_CODEC, path, def));
    }

    public ConfigValue<URI> value(String path, URI def) {
        return track(new ConfigValue<>(this, ValueCodec.URI_CODEC, path, def));
    }

    public ConfigValue<Path> value(String path, Path def) {
        return track(new ConfigValue<>(this, ValueCodec.PATH, path, def));
    }

    public ConfigValue<Pattern> value(String path, Pattern def) {
        return track(new ConfigValue<>(this, ValueCodec.PATTERN, path, def));
    }

    public ConfigValue<Charset> value(String path, Charset def) {
        return track(new ConfigValue<>(this, ValueCodec.CHARSET, path, def));
    }

    public ConfigValue<Locale> value(String path, Locale def) {
        return track(new ConfigValue<>(this, ValueCodec.LOCALE, path, def));
    }

    public ConfigValue<List<String>> value(String path, List<String> def) {
        return track(new ConfigValue<>(this, ValueCodec.listOf(ValueCodec.STRING), path, def));
    }

    public <E extends Enum<E>> ConfigValue<E> value(String path, E def) {
        return track(new ConfigValue<>(this, ValueCodec.ofEnum(def.getDeclaringClass()), path, def));
    }

    public <T> ConfigValue<T> value(String path, T def, ValueCodec<T> codec) {
        return track(new ConfigValue<>(this, codec, path, def));
    }

    public <T> ConfigValue<List<T>> value(String path, List<T> def, ValueCodec<T> elementCodec) {
        return track(new ConfigValue<>(this, ValueCodec.listOf(elementCodec), path, def));
    }

    public <T> ConfigValue<T> value(String path, T def) {
        return track(new ConfigValue<>(this, ValueCodec.passthrough(path, def), path, def));
    }

    private <V extends ConfigValue<?>> V track(V value) {
        pending.add(value);
        return value;
    }

    public ConfigView section(String path) {
        return new ConfigView(this, path);
    }
}
