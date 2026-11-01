package team.terrafirmagreg.konf.value;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import lombok.val;
import org.jspecify.annotations.Nullable;

public interface ValueCodec<T> {

    ValueCodec<Boolean> BOOLEAN = ofType(Boolean.class);
    ValueCodec<String> STRING = ofType(String.class);

    ValueCodec<Byte> BYTE = ofIntegral(Byte.MIN_VALUE, Byte.MAX_VALUE, Long::byteValue);
    ValueCodec<Short> SHORT = ofIntegral(Short.MIN_VALUE, Short.MAX_VALUE, Long::shortValue);
    ValueCodec<Integer> INTEGER = ofIntegral(Integer.MIN_VALUE, Integer.MAX_VALUE, Long::intValue);
    ValueCodec<Long> LONG = ofIntegral(Long.MIN_VALUE, Long.MAX_VALUE, v -> v);
    ValueCodec<Float> FLOAT = ofNumber(Number::floatValue);
    ValueCodec<Double> DOUBLE = ofNumber(Number::doubleValue);

    ValueCodec<BigInteger> BIG_INTEGER = new ValueCodec<>() {
        @Override
        public Object store(BigInteger value) {
            return value.toString();
        }

        @Override
        public @Nullable BigInteger load(Object raw) {
            if (raw instanceof BigInteger v) {
                return v;
            }
            if (raw instanceof String s) {
                return parse(s, BigInteger::new);
            }
            val v = integral(raw);
            return v != null ? BigInteger.valueOf(v) : null;
        }
    };

    ValueCodec<BigDecimal> BIG_DECIMAL = new ValueCodec<>() {
        @Override
        public Object store(BigDecimal value) {
            return value.toString();
        }

        @Override
        public @Nullable BigDecimal load(Object raw) {
            if (raw instanceof BigDecimal v) {
                return v;
            }
            if (raw instanceof String s) {
                return parse(s, BigDecimal::new);
            }
            if (raw instanceof Float || raw instanceof Double) {
                return BigDecimal.valueOf(((Number) raw).doubleValue());
            }
            return raw instanceof Number n ? new BigDecimal(n.toString()) : null;
        }
    };

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

    @Nullable
    T load(Object raw);

    static <T> ValueCodec<T> ofString(Function<String, T> parse, Function<T, String> format) {
        return new ValueCodec<>() {
            @Override
            public Object store(T value) {
                return format.apply(value);
            }

            @Override
            public @Nullable T load(Object raw) {
                return raw instanceof String s ? ValueCodec.parse(s, parse) : null;
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
            public @Nullable T load(Object raw) {
                return type.isInstance(raw) ? type.cast(raw) : null;
            }
        };
    }

    /** Lenient numeric conversion — {@code longValue}/{@code doubleValue} style narrowing is NOT bounds-checked. */
    static <T> ValueCodec<T> ofNumber(Function<Number, T> convert) {
        return new ValueCodec<>() {
            @Override
            public Object store(T value) {
                return value;
            }

            @Override
            public @Nullable T load(Object raw) {
                return raw instanceof Number n ? convert.apply(n) : null;
            }
        };
    }

    /** Strict integral codec — rejects Float/Double/String raws, bounds-checked narrowing. */
    static <T> ValueCodec<T> ofIntegral(long min, long max, Function<Long, T> convert) {
        return new ValueCodec<>() {
            @Override
            public Object store(T value) {
                return value;
            }

            @Override
            public @Nullable T load(Object raw) {
                val v = integral(raw);
                return v != null && v >= min && v <= max ? convert.apply(v) : null;
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
            public @Nullable E load(Object raw) {
                return raw instanceof String s ? parse(s, name -> Enum.valueOf(type, name)) : null;
            }
        };
    }

    /**
     * Passthrough codec for the generic {@code value(path, def)} contract — only native stored forms
     * (String/Boolean/Long/Double/List/Config) are legal; anything else is an {@link IllegalArgumentException} at
     * declare time.
     */
    static <T> ValueCodec<T> passthrough(String path, T def) {
        if (!(def instanceof List
                || def instanceof UnmodifiableConfig
                || def instanceof String
                || def instanceof Boolean
                || def instanceof Long
                || def instanceof Double)) {
            throw new IllegalArgumentException(
                    "No native stored form for " + def.getClass().getName() + " at '" + path + "' — pass a ValueCodec");
        }
        val type = def.getClass();
        return new ValueCodec<>() {
            @Override
            public Object store(T value) {
                return value;
            }

            @Override
            @SuppressWarnings("unchecked")
            public @Nullable T load(Object raw) {
                return type.isInstance(raw)
                                || (raw instanceof List && def instanceof List)
                                || (raw instanceof UnmodifiableConfig && def instanceof UnmodifiableConfig)
                        ? (T) raw
                        : null;
            }
        };
    }

    static <T> ValueCodec<List<T>> listOf(ValueCodec<T> elementCodec) {
        return new ValueCodec<>() {
            @Override
            public Object store(List<T> value) {
                val out = new ArrayList<>(value.size());
                value.forEach(v -> out.add(elementCodec.store(v)));
                return out;
            }

            @Override
            public @Nullable List<T> load(Object raw) {
                if (!(raw instanceof List<?> list)) {
                    return null;
                }
                val out = new ArrayList<T>(list.size());
                for (val r : list) {
                    T v = elementCodec.load(r);
                    if (v == null) {
                        return null;
                    }
                    out.add(v);
                }
                return List.copyOf(out);
            }
        };
    }

    static <T> ValueCodec<T> bean(Supplier<T> ctor) {
        return Beans.codec(ctor);
    }

    static <R extends Record> ValueCodec<R> bean(Class<R> type) {
        return Beans.codec(type);
    }

    static <T> ValueCodec<Map<String, T>> mapOf(ValueCodec<T> elementCodec) {
        return new ValueCodec<>() {
            @Override
            public Object store(Map<String, T> value) {
                val out = CommentedConfig.inMemory();
                value.forEach((k, v) -> out.set(List.of(k), v == null ? null : elementCodec.store(v)));
                return out;
            }

            @Override
            public @Nullable Map<String, T> load(Object raw) {
                val out = new LinkedHashMap<String, T>();
                if (raw instanceof UnmodifiableConfig cfg) {
                    for (val e : cfg.entrySet()) {
                        T v = elementCodec.load(e.getValue());
                        if (v == null) {
                            return null;
                        }
                        out.put(e.getKey(), v);
                    }
                    return out;
                }
                if (raw instanceof Map<?, ?> map) {
                    for (val e : map.entrySet()) {
                        T v = elementCodec.load(e.getValue());
                        if (v == null) {
                            return null;
                        }
                        out.put(String.valueOf(e.getKey()), v);
                    }
                    return out;
                }
                return null;
            }
        };
    }

    private static @Nullable Long integral(Object raw) {
        if (raw instanceof Byte || raw instanceof Short || raw instanceof Integer || raw instanceof Long) {
            return ((Number) raw).longValue();
        }
        if (raw instanceof BigInteger bi) {
            try {
                return bi.longValueExact();
            } catch (ArithmeticException e) {
                return null;
            }
        }
        return null;
    }

    private static <T> @Nullable T parse(String s, Function<String, T> parse) {
        try {
            return parse.apply(s);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
