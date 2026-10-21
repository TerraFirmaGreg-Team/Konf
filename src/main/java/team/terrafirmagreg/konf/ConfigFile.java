package team.terrafirmagreg.konf;

import com.electronwill.nightconfig.core.Config;
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
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.jspecify.annotations.Nullable;
import team.terrafirmagreg.konf.format.ConfigFormat;
import team.terrafirmagreg.konf.value.ConfigValue;
import team.terrafirmagreg.konf.value.ValueBuilder;
import team.terrafirmagreg.konf.value.ValueBuilder.Integral;
import team.terrafirmagreg.konf.value.ValueBuilder.Real;
import team.terrafirmagreg.konf.value.ValueCodec;
import team.terrafirmagreg.konf.value.Values;

/**
 * One opened config — file-backed or in-memory. Values bind through {@link #value} factories into live
 * {@link ConfigValue}s: absent keys are materialized with defaults on first read or {@link #sync()}, and invalid file
 * content is reset to defaults on read.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class ConfigFile {

    private final @Nullable Path path;
    private final ConfigFormat format;
    private final Config config;

    @Getter(AccessLevel.NONE)
    private final List<ValueBuilder<?>> pending;

    public void load() {
        ifFile(FileConfig::load);
    }

    public void save() {
        ifFile(FileConfig::save);
    }

    /**
     * Re-reads the file (no-op for in-memory) and fires every declared value's {@code onChange} with its fresh value.
     */
    public void reload() {
        load();
        fireChanges();
    }

    /** Materializes every declared value (writes missing defaults/comments) and saves the file. */
    public void sync() {
        pending.forEach(b -> b.entry().get());
        save();
    }

    void fireChanges() {
        pending.forEach(ValueBuilder::fireChange);
    }

    /** Stops the file watcher — only matters with {@code autoreload()}; also fires automatically on GC. */
    public void close() {
        ifFile(FileConfig::close);
    }

    private void ifFile(Consumer<FileConfig> op) {
        if (config instanceof FileConfig file) {
            op.accept(file);
        }
    }

    /** Raw read — no default materialization, no coercion. */
    public <T> T get(String path) {
        return config.get(path);
    }

    public void set(String path, Object value) {
        config.set(path, value);
    }

    public boolean contains(String path) {
        return config.contains(path);
    }

    public ValueBuilder<Boolean> value(String path, boolean def) {
        return track(new ValueBuilder<>(b -> Values.bool(this, b), path, def));
    }

    public ValueBuilder<Integer> value(String path, int def) {
        return track(new ValueBuilder<>(b -> Values.integer(this, b), path, def));
    }

    public Integral value(String path, long def) {
        return track(new Integral(b -> Values.longValue(this, b), path, def));
    }

    public Real value(String path, double def) {
        return track(new Real(b -> Values.real(this, b), path, def));
    }

    public ValueBuilder<Float> value(String path, float def) {
        return track(new ValueBuilder<>(b -> Values.floatValue(this, b), path, def));
    }

    public ValueBuilder<Byte> value(String path, byte def) {
        return track(new ValueBuilder<>(b -> Values.byteValue(this, b), path, def));
    }

    public ValueBuilder<Short> value(String path, short def) {
        return track(new ValueBuilder<>(b -> Values.shortValue(this, b), path, def));
    }

    public ValueBuilder<String> value(String path, String def) {
        return track(new ValueBuilder<>(b -> Values.string(this, b), path, def));
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
        return track(new ValueBuilder<>(b -> Values.stringList(this, b), path, def));
    }

    /** Enum values are stored by {@code name()} and fall back to the default on unknown names. */
    public <E extends Enum<E>> ValueBuilder<E> value(String path, E def) {
        return track(new ValueBuilder<>(b -> Values.enumValue(this, b), path, def));
    }

    /** Custom type through a codec — e.g. {@code ValueCodec.ofString(Duration::parse, Duration::toString)}. */
    public <T> ValueBuilder<T> value(String path, T def, ValueCodec<T> codec) {
        return track(new ValueBuilder<>(b -> Values.coded(this, b, codec), path, def));
    }

    /**
     * List of codec-mapped elements — e.g. {@code value("modes", List.of(Mode.NORMAL), ofEnum(Mode.class))}. With an
     * inexact codec expression ({@code ofString(...)} inline) javac can't pick between this and the scalar codec
     * overload — witness it: {@code ValueCodec.<Mode>ofString(...)}.
     */
    public <T> ValueBuilder<List<T>> value(String path, List<T> def, ValueCodec<T> elementCodec) {
        return track(new ValueBuilder<>(b -> Values.typedList(this, b, elementCodec), path, def));
    }

    /**
     * Escape hatch for any type the format stores natively (maps, sub-configs, lists of numbers). Only exact class
     * matches coerce — the concrete overloads above are always picked first.
     */
    public <T> ValueBuilder<T> value(String path, T def) {
        return track(new ValueBuilder<>(b -> Values.generic(this, b), path, def));
    }

    private <B extends ValueBuilder<?>> B track(B builder) {
        pending.add(builder);
        return builder;
    }
}
