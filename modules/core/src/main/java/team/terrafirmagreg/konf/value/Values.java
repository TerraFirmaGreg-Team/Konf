package team.terrafirmagreg.konf.value;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.UnmodifiableCommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.file.FileConfig;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import team.terrafirmagreg.konf.ConfigFile;
import team.terrafirmagreg.konf.InvalidConfigException;

@Slf4j
@UtilityClass
public class Values {

    private static final Pattern COMMENT_INDENT = Pattern.compile("(?m)^(\\S)");

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

    @SuppressWarnings("unchecked")
    public static <T> ConfigValue<T> generic(Backend backend, ValueBuilder<T> b) {
        val def = b.defaultValue();
        if (!(def instanceof List
                || def instanceof UnmodifiableConfig
                || def instanceof String
                || def instanceof Boolean
                || def instanceof Long
                || def instanceof Double)) {
            throw new IllegalArgumentException(
                    "No native stored form for " + def.getClass().getName() + " at '" + b.path()
                            + "' — pass a ValueCodec or declare the subtree with subconfig()");
        }
        val type = (Class<T>) def.getClass();
        return bind(
                backend,
                b,
                raw -> type.isInstance(raw)
                                || (raw instanceof List && b.defaultValue() instanceof List)
                                || (raw instanceof UnmodifiableConfig && b.defaultValue() instanceof UnmodifiableConfig)
                        ? (T) raw
                        : null,
                Values::store);
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
        val defStored = store.apply(b.defaultValue());
        return ConfigValue.of(
                b.defaultValue(), () -> read(backend, b, load, store, defStored), v -> write(backend, b, store, v));
    }

    public record Backend(Config config, Runnable saver, String name, boolean strict) {
        public static Backend of(ConfigFile file) {
            val config = file.config();
            Runnable saver = config instanceof FileConfig fc ? fc::save : () -> {};
            val path = file.path();
            return new Backend(config, saver, path != null ? path.getFileName().toString() : "<memory>", file.strict());
        }
    }

    private static <T> T read(
            Backend backend, ValueBuilder<T> b, Function<Object, T> load, Function<T, Object> store, Object defStored) {
        val config = backend.config();
        val path = b.path();
        if (!config.contains(path)) {
            config.set(path, defStored);
            comment(config, b);
            return b.defaultValue();
        }
        Object raw = config.get(path);
        if (b.mergeBy() != null) {
            val merged = mergeNamed(defStored, raw, b.mergeBy(), config::createSubConfig);
            if (merged != raw) {
                config.set(path, merged);
                raw = merged;
            }
        }
        T value;
        try {
            value = load.apply(raw);
        } catch (RuntimeException e) {
            value = null;
        }
        T clamped = value == null ? null : clamp(b, value);
        if (backend.strict() && (clamped == null || !b.validator().test(clamped) || !Objects.equals(clamped, value))) {
            throw InvalidConfigException.of(path, backend.name(), raw);
        }
        if (clamped == null || !b.validator().test(clamped)) {
            log.warn("Invalid value '{}' at '{}' in {} — resetting to {}", raw, path, backend.name(), b.defaultValue());
            config.set(path, defStored);
            return b.defaultValue();
        }
        if (!Objects.equals(clamped, value)) {
            config.set(path, store.apply(clamped));
        }
        return clamped;
    }

    private static <T> void write(Backend backend, ValueBuilder<T> b, Function<T, Object> store, T value) {
        T checked = clamp(b, Objects.requireNonNull(value, "null value at '" + b.path() + "'"));
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
            commented.setComment(b.path(), indentComment(b.comment()));
        }
    }

    public static String indentComment(String comment) {
        return COMMENT_INDENT.matcher(comment).replaceAll(" $1");
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

    public static void reconcile(Config dst, UnmodifiableConfig doc) {
        for (val e : doc.entrySet()) {
            val key = List.of(e.getKey());
            Object cur = dst.getRaw(key);
            if (cur instanceof Config curSub && e.getValue() instanceof UnmodifiableConfig docSub) {
                copyComment(dst, doc, key);
                reconcile(curSub, docSub);
            } else if (cur instanceof List<?> curList && e.getValue() instanceof List<?> docList) {
                mergeNamedList(dst, key, curList, docList);
            } else if (cur == null) {
                dst.set(key, deepCopy(e.getValue(), dst::createSubConfig));
                copyComment(dst, doc, key);
            }
        }
    }

    private static void mergeNamedList(Config dst, List<String> key, List<?> curList, List<?> docList) {
        val out = new ArrayList<Object>(curList);
        boolean changed = false;
        for (val d : docList) {
            if (!(d instanceof UnmodifiableConfig dc) || !(dc.get(List.of("name")) instanceof String name)) {
                return;
            }
            Config match = null;
            for (val c : curList) {
                if (c instanceof Config cc && name.equals(cc.get(List.of("name")))) {
                    match = cc;
                    break;
                }
            }
            if (match == null) {
                out.add(deepCopy(d, dst::createSubConfig));
                changed = true;
            } else {
                reconcile(match, dc);
            }
        }
        if (changed) {
            dst.set(key, out);
        }
    }

    private static void copyComment(Config dst, UnmodifiableConfig doc, List<String> key) {
        if (dst instanceof CommentedConfig d
                && doc instanceof UnmodifiableCommentedConfig s
                && d.getComment(key) == null) {
            d.setComment(key, s.getComment(key));
        }
    }

    private static Object mergeNamed(Object defStored, Object raw, String nameKey, Supplier<Config> sub) {
        if (!(defStored instanceof List<?> defList) || !(raw instanceof List<?> rawList)) {
            return raw;
        }
        for (val d : defList) {
            if (!(d instanceof UnmodifiableConfig dc) || !(dc.get(List.of(nameKey)) instanceof String)) {
                return raw;
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
            if (!names.contains(((UnmodifiableConfig) d).get(List.of(nameKey)))) {
                if (merged == null) {
                    merged = new ArrayList<>(rawList);
                }
                merged.add(deepCopy(d, sub));
            }
        }
        return merged != null ? merged : raw;
    }

    private static Object deepCopy(Object v, Supplier<Config> sub) {
        if (v instanceof UnmodifiableConfig src) {
            val copy = sub.get();
            for (val e : src.entrySet()) {
                val key = List.of(e.getKey());
                copy.set(key, deepCopy(e.getValue(), sub));
                if (src instanceof UnmodifiableCommentedConfig s && copy instanceof CommentedConfig c) {
                    val comment = s.getComment(key);
                    if (comment != null) {
                        c.setComment(key, comment);
                    }
                }
            }
            return copy;
        }
        if (v instanceof List<?> list) {
            val out = new ArrayList<>(list.size());
            list.forEach(o -> out.add(deepCopy(o, sub)));
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
