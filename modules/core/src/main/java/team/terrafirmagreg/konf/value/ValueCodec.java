package team.terrafirmagreg.konf.value;

import com.electronwill.nightconfig.core.CommentedConfig;
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

public interface ValueCodec<T> {

    ValueCodec<Boolean> BOOLEAN = ofType(Boolean.class);
    ValueCodec<String> STRING = ofType(String.class);
    ValueCodec<CommentedConfig> CONFIG = ofType(CommentedConfig.class);

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

    ValueCodec<UUID> UUID_CODEC = ofString(UUID::fromString, UUID::toString);
    ValueCodec<URI> URI_CODEC = ofString(URI::create, URI::toString);
    ValueCodec<Path> PATH = ofString(Path::of, Path::toString);
    ValueCodec<Pattern> PATTERN = ofString(Pattern::compile, Pattern::pattern);
    ValueCodec<Charset> CHARSET = ofString(Charset::forName, Charset::name);
    ValueCodec<Locale> LOCALE = ofString(Locale::forLanguageTag, Locale::toLanguageTag);

    Object store(T value);

    T load(Object raw);

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

    static <T> ValueCodec<T> bean(Class<T> type) {
        return Beans.codec(type);
    }
}
