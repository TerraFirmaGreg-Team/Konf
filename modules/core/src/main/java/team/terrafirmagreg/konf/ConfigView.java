package team.terrafirmagreg.konf;

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
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;
import team.terrafirmagreg.konf.value.ConfigValue;
import team.terrafirmagreg.konf.value.ValueCodec;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class ConfigView {

    @NonNull
    ConfigFile file;

    @NonNull
    String prefix;

    public ConfigView section(@NonNull String path) {
        return new ConfigView(file, path(path));
    }

    private String path(String leaf) {
        return prefix + "." + leaf;
    }

    public ConfigValue<Boolean> value(String path, boolean def) {
        return file.value(path(path), def);
    }

    public ConfigValue<Integer> value(String path, int def) {
        return file.value(path(path), def);
    }

    public ConfigValue<Long> value(String path, long def) {
        return file.value(path(path), def);
    }

    public ConfigValue<Double> value(String path, double def) {
        return file.value(path(path), def);
    }

    public ConfigValue<Float> value(String path, float def) {
        return file.value(path(path), def);
    }

    public ConfigValue<Byte> value(String path, byte def) {
        return file.value(path(path), def);
    }

    public ConfigValue<Short> value(String path, short def) {
        return file.value(path(path), def);
    }

    public ConfigValue<String> value(String path, String def) {
        return file.value(path(path), def);
    }

    public ConfigValue<BigInteger> value(String path, BigInteger def) {
        return file.value(path(path), def);
    }

    public ConfigValue<BigDecimal> value(String path, BigDecimal def) {
        return file.value(path(path), def);
    }

    public ConfigValue<Duration> value(String path, Duration def) {
        return file.value(path(path), def);
    }

    public ConfigValue<Period> value(String path, Period def) {
        return file.value(path(path), def);
    }

    public ConfigValue<Instant> value(String path, Instant def) {
        return file.value(path(path), def);
    }

    public ConfigValue<LocalDate> value(String path, LocalDate def) {
        return file.value(path(path), def);
    }

    public ConfigValue<LocalTime> value(String path, LocalTime def) {
        return file.value(path(path), def);
    }

    public ConfigValue<LocalDateTime> value(String path, LocalDateTime def) {
        return file.value(path(path), def);
    }

    public ConfigValue<OffsetDateTime> value(String path, OffsetDateTime def) {
        return file.value(path(path), def);
    }

    public ConfigValue<ZoneId> value(String path, ZoneId def) {
        return file.value(path(path), def);
    }

    public ConfigValue<UUID> value(String path, UUID def) {
        return file.value(path(path), def);
    }

    public ConfigValue<URI> value(String path, URI def) {
        return file.value(path(path), def);
    }

    public ConfigValue<Path> value(String path, Path def) {
        return file.value(path(path), def);
    }

    public ConfigValue<Pattern> value(String path, Pattern def) {
        return file.value(path(path), def);
    }

    public ConfigValue<Charset> value(String path, Charset def) {
        return file.value(path(path), def);
    }

    public ConfigValue<Locale> value(String path, Locale def) {
        return file.value(path(path), def);
    }

    public ConfigValue<List<String>> value(String path, List<String> def) {
        return file.value(path(path), def);
    }

    public <E extends Enum<E>> ConfigValue<E> value(String path, E def) {
        return file.value(path(path), def);
    }

    public <T> ConfigValue<T> value(String path, T def, ValueCodec<T> codec) {
        return file.value(path(path), def, codec);
    }

    public <T> ConfigValue<List<T>> value(String path, List<T> def, ValueCodec<T> elementCodec) {
        return file.value(path(path), def, elementCodec);
    }

    public <T> ConfigValue<T> value(String path, T def) {
        return file.value(path(path), def);
    }
}
