package team.terrafirmagreg.konf.value;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.file.FileConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import team.terrafirmagreg.konf.ConfigFile;

/** Coercion, clamping and validation shared by bound values — invalid file content resets to default. */
@Slf4j
@UtilityClass
public class Values {

    public static ConfigValue<Boolean> bool(ConfigFile file, ValueBuilder<Boolean> b) {
        return bind(file, b, raw -> raw instanceof Boolean v ? v : null, Values::store);
    }

    public static ConfigValue<Integer> integer(ConfigFile file, ValueBuilder<Integer> b) {
        return bind(file, b, raw -> raw instanceof Number n ? n.intValue() : null, Values::store);
    }

    public static ConfigValue<Long> longValue(ConfigFile file, ValueBuilder<Long> b) {
        return bind(file, b, raw -> raw instanceof Number n ? n.longValue() : null, Values::store);
    }

    public static ConfigValue<Double> real(ConfigFile file, ValueBuilder<Double> b) {
        return bind(file, b, raw -> raw instanceof Number n ? n.doubleValue() : null, Values::store);
    }

    public static ConfigValue<Float> floatValue(ConfigFile file, ValueBuilder<Float> b) {
        return bind(file, b, raw -> raw instanceof Number n ? n.floatValue() : null, Values::store);
    }

    public static ConfigValue<Byte> byteValue(ConfigFile file, ValueBuilder<Byte> b) {
        return bind(file, b, raw -> raw instanceof Number n ? n.byteValue() : null, Values::store);
    }

    public static ConfigValue<Short> shortValue(ConfigFile file, ValueBuilder<Short> b) {
        return bind(file, b, raw -> raw instanceof Number n ? n.shortValue() : null, Values::store);
    }

    public static ConfigValue<String> string(ConfigFile file, ValueBuilder<String> b) {
        return bind(file, b, raw -> raw instanceof String v ? v : null, Values::store);
    }

    public static ConfigValue<List<String>> stringList(ConfigFile file, ValueBuilder<List<String>> b) {
        return bind(
                file,
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

    public static <E extends Enum<E>> ConfigValue<E> enumValue(ConfigFile file, ValueBuilder<E> b) {
        @SuppressWarnings("unchecked")
        val type = (Class<E>) b.defaultValue().getDeclaringClass();
        return bind(file, b, raw -> raw instanceof String name ? parse(type, name) : null, Values::store);
    }

    public static <T> ConfigValue<T> generic(ConfigFile file, ValueBuilder<T> b) {
        @SuppressWarnings("unchecked")
        val type = (Class<T>) b.defaultValue().getClass();
        return bind(file, b, raw -> type.isInstance(raw) ? type.cast(raw) : null, Values::store);
    }

    public static <T> ConfigValue<T> coded(ConfigFile file, ValueBuilder<T> b, ValueCodec<T> codec) {
        return bind(file, b, codec::load, codec::store);
    }

    public static <T> ConfigValue<List<T>> typedList(
            ConfigFile file, ValueBuilder<List<T>> b, ValueCodec<T> elementCodec) {
        return bind(
                file,
                b,
                raw -> raw instanceof List<?> list ? mapAll(list, elementCodec) : null,
                v -> v.stream().map(elementCodec::store).toList());
    }

    private static <T> ConfigValue<T> bind(
            ConfigFile file, ValueBuilder<T> b, Function<Object, T> load, Function<T, Object> store) {
        // Entries must not retain the ConfigFile wrapper — the file watcher has to let it be GC'd.
        val backend = Backend.of(file);
        return ConfigValue.of(b.defaultValue(), () -> read(backend, b, load, store), v -> write(backend, b, store, v));
    }

    private record Backend(Config config, Runnable saver, String name) {
        static Backend of(ConfigFile file) {
            val config = file.config();
            Runnable saver = config instanceof FileConfig fc ? fc::save : () -> {};
            val path = file.path();
            return new Backend(config, saver, path != null ? path.getFileName().toString() : "<memory>");
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
        T value;
        try {
            value = load.apply(config.get(path));
        } catch (RuntimeException e) { // codecs like Duration::parse throw on garbage instead of returning null
            value = null;
        }
        T clamped = value == null ? null : clamp(b, value);
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
            commented.setComment(b.path(), " " + b.comment().replace("\n", "\n "));
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
