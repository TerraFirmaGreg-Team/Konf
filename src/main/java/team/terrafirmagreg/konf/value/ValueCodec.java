package team.terrafirmagreg.konf.value;

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
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Bidirectional mapping between a declared value and the form night-config stores natively. {@link #load} returns
 * {@code null} for unreadable content — the entry then resets to its default. Constants cover common types so
 * {@code list(path, defs, ValueCodec.DURATION)} works out of the box.
 */
public interface ValueCodec<T> {

    ValueCodec<Boolean> BOOLEAN = ofType(Boolean.class);
    ValueCodec<String> STRING = ofType(String.class);

    ValueCodec<Byte> BYTE = ofNumber(Number::byteValue);
    ValueCodec<Short> SHORT = ofNumber(Number::shortValue);
    ValueCodec<Integer> INTEGER = ofNumber(Number::intValue);
    ValueCodec<Long> LONG = ofNumber(Number::longValue);
    ValueCodec<Float> FLOAT = ofNumber(Number::floatValue);
    ValueCodec<Double> DOUBLE = ofNumber(Number::doubleValue);

    ValueCodec<BigInteger> BIG_INTEGER = ofString(BigInteger::new, BigInteger::toString);
    ValueCodec<BigDecimal> BIG_DECIMAL = ofString(BigDecimal::new, BigDecimal::toString);

    ValueCodec<Duration> DURATION = ofString(Duration::parse, Duration::toString);
    ValueCodec<Period> PERIOD = ofString(Period::parse, Period::toString);
    ValueCodec<Instant> INSTANT = ofString(Instant::parse, Instant::toString);
    ValueCodec<LocalDate> LOCAL_DATE = ofString(LocalDate::parse, LocalDate::toString);
    ValueCodec<LocalTime> LOCAL_TIME = ofString(LocalTime::parse, LocalTime::toString);
    ValueCodec<LocalDateTime> LOCAL_DATE_TIME = ofString(LocalDateTime::parse, LocalDateTime::toString);
    ValueCodec<OffsetDateTime> OFFSET_DATE_TIME = ofString(OffsetDateTime::parse, OffsetDateTime::toString);
    ValueCodec<ZoneId> ZONE_ID = ofString(ZoneId::of, ZoneId::toString);

    // *_CODEC suffix: UUID/URI type names are already uppercase — a bare constant would self-shadow
    ValueCodec<UUID> UUID_CODEC = ofString(UUID::fromString, UUID::toString);
    ValueCodec<URI> URI_CODEC = ofString(URI::create, URI::toString);
    ValueCodec<Path> PATH = ofString(Path::of, Path::toString);
    ValueCodec<Pattern> PATTERN = ofString(Pattern::compile, Pattern::pattern);
    ValueCodec<Charset> CHARSET = ofString(Charset::forName, Charset::name);
    ValueCodec<Locale> LOCALE = ofString(Locale::forLanguageTag, Locale::toLanguageTag);

    Object store(T value);

    T load(Object raw);

    /** String-backed codec, e.g. {@code ofString(Duration::parse, Duration::toString)}. */
    static <T> ValueCodec<T> ofString(Function<String, T> parse, Function<T, String> format) {
        return new ValueCodec<>() {
            @Override
            public Object store(T value) {
                return format.apply(value);
            }

            @Override
            public T load(Object raw) {
                return raw instanceof String s ? parse.apply(s) : null;
            }
        };
    }

    /** Pass-through codec for exact runtime classes ({@code String}, {@code Boolean}). */
    static <T> ValueCodec<T> ofType(Class<T> type) {
        return new ValueCodec<>() {
            @Override
            public Object store(T value) {
                return value;
            }

            @Override
            public T load(Object raw) {
                return type.isInstance(raw) ? type.cast(raw) : null;
            }
        };
    }

    /** File numbers coerce through {@link Number}, e.g. {@code ofNumber(Number::intValue)}. */
    static <T> ValueCodec<T> ofNumber(Function<Number, T> convert) {
        return new ValueCodec<>() {
            @Override
            public Object store(T value) {
                return value;
            }

            @Override
            public T load(Object raw) {
                return raw instanceof Number n ? convert.apply(n) : null;
            }
        };
    }

    /** Enum codec storing {@code name()} — e.g. {@code list("modes", defs, ValueCodec.ofEnum(Mode.class))}. */
    static <E extends Enum<E>> ValueCodec<E> ofEnum(Class<E> type) {
        return new ValueCodec<>() {
            @Override
            public Object store(E value) {
                return value.name();
            }

            @Override
            public E load(Object raw) {
                return raw instanceof String s ? Enum.valueOf(type, s) : null;
            }
        };
    }
}
