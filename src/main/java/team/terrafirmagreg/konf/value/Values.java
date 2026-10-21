package team.terrafirmagreg.konf.value;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.file.FileConfig;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import team.terrafirmagreg.konf.ConfigFile;
import team.terrafirmagreg.konf.InvalidConfigException;

/** Coercion, clamping and validation shared by bound values — invalid file content resets to default. */
@Slf4j
@UtilityClass
public class Values {

    public static ConfigValue<Boolean> bool(Backend backend, ValueBuilder<Boolean> b) {
        return bind(backend, b, raw -> raw instanceof Boolean v ? v : null, Values::store);
    }

    public static ConfigValue<Integer> integer(Backend backend, ValueBuilder<Integer> b) {
        return bind(backend, b, raw -> raw instanceof Number n ? n.intValue() : null, Values::store);
    }

    public static ConfigValue<Long> longValue(Backend backend, ValueBuilder<Long> b) {
        return bind(backend, b, raw -> raw instanceof Number n ? n.longValue() : null, Values::store);
    }

    public static ConfigValue<Double> real(Backend backend, ValueBuilder<Double> b) {
        return bind(backend, b, raw -> raw instanceof Number n ? n.doubleValue() : null, Values::store);
    }

    public static ConfigValue<Float> floatValue(Backend backend, ValueBuilder<Float> b) {
        return bind(backend, b, raw -> raw instanceof Number n ? n.floatValue() : null, Values::store);
    }

    public static ConfigValue<Byte> byteValue(Backend backend, ValueBuilder<Byte> b) {
        return bind(backend, b, raw -> raw instanceof Number n ? n.byteValue() : null, Values::store);
    }

    public static ConfigValue<Short> shortValue(Backend backend, ValueBuilder<Short> b) {
        return bind(backend, b, raw -> raw instanceof Number n ? n.shortValue() : null, Values::store);
    }

    public static ConfigValue<String> string(Backend backend, ValueBuilder<String> b) {
        return bind(backend, b, raw -> raw instanceof String v ? v : null, Values::store);
    }

    public static ConfigValue<List<String>> stringList(Backend backend, ValueBuilder<List<String>> b) {
        return bind(
                backend,
                b,
                raw -> {
                    if (raw instanceof List<?> list && list.stream().allMatch(String.class::isInstance)) {
                        @SuppressWarnings("unchecked")
                        val strings = List.copyOf((List<String>) list);
                        return strings;
                    }
                    return null;
                },
                Values::store);
    }

    public static <E extends Enum<E>> ConfigValue<E> enumValue(Backend backend, ValueBuilder<E> b) {
        @SuppressWarnings("unchecked")
        val type = (Class<E>) b.defaultValue().getDeclaringClass();
        return bind(backend, b, raw -> raw instanceof String name ? parse(type, name) : null, Values::store);
    }

    public static <T> ConfigValue<T> generic(Backend backend, ValueBuilder<T> b) {
        @SuppressWarnings("unchecked")
        val type = (Class<T>) b.defaultValue().getClass();
        return bind(backend, b, raw -> type.isInstance(raw) ? type.cast(raw) : null, Values::store);
    }

    public static <T> ConfigValue<T> coded(Backend backend, ValueBuilder<T> b, ValueCodec<T> codec) {
        return bind(backend, b, codec::load, codec::store);
    }

    public static <T> ConfigValue<List<T>> typedList(
            Backend backend, ValueBuilder<List<T>> b, ValueCodec<T> elementCodec) {
        return bind(
                backend,
                b,
                raw -> raw instanceof List<?> list ? mapAll(list, elementCodec) : null,
                v -> v.stream().map(elementCodec::store).toList());
    }

    private static <T> ConfigValue<T> bind(
            Backend backend, ValueBuilder<T> b, Function<Object, T> load, Function<T, Object> store) {
        return ConfigValue.of(b.defaultValue(), () -> read(backend, b, load, store), v -> write(backend, b, store, v));
    }

    /** Shared write/read state — holds no {@code ConfigFile} reference so entries never pin the wrapper for GC. */
    public record Backend(Config config, Runnable saver, String name, boolean strict) {
        public static Backend of(ConfigFile file) {
            val config = file.config();
            Runnable saver = config instanceof FileConfig fc ? fc::save : () -> {};
            val path = file.path();
            return new Backend(config, saver, path != null ? path.getFileName().toString() : "<memory>", file.strict());
        }
    }

    private static <T> T read(Backend backend, ValueBuilder<T> b, Function<Object, T> load, Function<T, Object> store) {
        val config = backend.config();
        val path = b.path();
        if (!config.contains(path)) {
            config.set(path, store.apply(b.defaultValue()));
            comment(config, b);
            return b.defaultValue();
        }
        Object raw = config.get(path);
        if (b.mergeBy() != null) {
            val merged = mergeNamed(store.apply(b.defaultValue()), raw, b.mergeBy());
            if (merged != raw) {
                config.set(path, merged);
                raw = merged;
            }
        }
        T value;
        try {
            value = load.apply(raw);
        } catch (RuntimeException e) { // codecs like Duration::parse throw on garbage instead of returning null
            value = null;
        }
        T clamped = value == null ? null : clamp(b, value);
        if (backend.strict() && (clamped == null || !b.validator().test(clamped) || !Objects.equals(clamped, value))) {
            throw InvalidConfigException.of(path, backend.name(), config.get(path));
        }
        if (clamped == null || !b.validator().test(clamped)) {
            log.warn(
                    "Invalid value '{}' at '{}' in {} — resetting to {}",
                    config.get(path),
                    path,
                    backend.name(),
                    b.defaultValue());
            config.set(path, store.apply(b.defaultValue()));
            return b.defaultValue();
        }
        if (!Objects.equals(clamped, value)) {
            config.set(path, store.apply(clamped));
        }
        return clamped;
    }

    private static <T> void write(Backend backend, ValueBuilder<T> b, Function<T, Object> store, T value) {
        T checked = clamp(b, value);
        if (!b.validator().test(checked)) {
            throw new IllegalArgumentException("Value " + value + " rejected by validator at '" + b.path() + "'");
        }
        backend.config().set(b.path(), store.apply(checked));
        backend.saver().run();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> T clamp(ValueBuilder<T> b, T value) {
        val min = b.min();
        val max = b.max();
        if (min == null && max == null) {
            return value;
        }
        val comparable = (Comparable) value;
        if (min != null && comparable.compareTo(min) < 0) {
            return min;
        }
        if (max != null && comparable.compareTo(max) > 0) {
            return max;
        }
        return value;
    }

    private static void comment(Config config, ValueBuilder<?> b) {
        if (config instanceof CommentedConfig commented && !b.comment().isEmpty()) {
            commented.setComment(b.path(), b.comment().replaceAll("(?m)^(\\S)", " $1"));
        }
    }

    private static <T> List<T> mapAll(List<?> list, ValueCodec<T> codec) {
        val out = new ArrayList<T>(list.size());
        for (val raw : list) {
            T value = codec.load(raw);
            if (value == null) {
                return null;
            }
            out.add(value);
        }
        return List.copyOf(out);
    }

    /** Append default table entries whose {@code nameKey} is absent from the file's list; other shapes pass through. */
    private static Object mergeNamed(Object defStored, Object raw, String nameKey) {
        if (!(defStored instanceof List<?> defList) || !(raw instanceof List<?> rawList)) {
            return raw;
        }
        for (val d : defList) {
            if (!(d instanceof Config) || !(((Config) d).get(List.of(nameKey)) instanceof String)) {
                return raw; // not a named-entry list — leave the user list alone
            }
        }
        val names = new HashSet<String>();
        for (val t : rawList) {
            if (t instanceof Config tc && tc.get(List.of(nameKey)) instanceof String tn) {
                names.add(tn);
            }
        }
        List<Object> merged = null;
        for (val d : defList) {
            if (!names.contains(((Config) d).get(List.of(nameKey)))) {
                if (merged == null) {
                    merged = new ArrayList<>(rawList);
                }
                merged.add(deepCopy(d));
            }
        }
        return merged != null ? merged : raw;
    }

    private static Object deepCopy(Object v) {
        if (v instanceof Config src) {
            val copy = src.configFormat().createConfig(LinkedHashMap::new);
            for (val e : src.entrySet()) {
                copy.set(List.of(e.getKey()), deepCopy(e.getValue()));
            }
            if (src instanceof CommentedConfig s && copy instanceof CommentedConfig c) {
                for (val e : src.entrySet()) {
                    val comment = s.getComment(List.of(e.getKey()));
                    if (comment != null) {
                        c.setComment(List.of(e.getKey()), comment);
                    }
                }
            }
            return copy;
        }
        if (v instanceof List<?> list) {
            val out = new ArrayList<>(list.size());
            list.forEach(o -> out.add(deepCopy(o)));
            return out;
        }
        return v;
    }

    private static Object store(Object value) {
        return value instanceof Enum<?> e ? e.name() : value;
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String name) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
