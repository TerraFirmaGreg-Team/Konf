package team.terrafirmagreg.konf.value;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import team.terrafirmagreg.konf.ConfigFile;
import team.terrafirmagreg.konf.InvalidConfigException;

@Slf4j
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public final class ConfigValue<T> implements Supplier<T> {

    @Getter(AccessLevel.NONE)
    final @NonNull ConfigFile file;

    @Getter(AccessLevel.NONE)
    final @NonNull ValueCodec<T> codec;

    final @NonNull String path;
    final @NonNull T defaultValue;
    T min;
    T max;

    @Getter(AccessLevel.NONE)
    String comment = "";

    @Getter(AccessLevel.NONE)
    Predicate<T> validator = v -> true;

    public ConfigValue<T> range(@NonNull T min, @NonNull T max) {
        if (!(min instanceof Comparable) || !(max instanceof Comparable)) {
            throw new IllegalArgumentException("range() requires Comparable bounds at '" + path + "'");
        }
        this.min = min;
        this.max = max;
        return this;
    }

    public ConfigValue<T> comment(@NonNull String... lines) {
        this.comment = String.join("\n", lines);
        return this;
    }

    public String comment() {
        return comment;
    }

    public ConfigValue<T> validate(@NonNull Predicate<T> validator) {
        this.validator = validator;
        return this;
    }

    @Override
    public T get() {
        val config = file.config();
        if (!config.contains(path)) {
            config.set(path, codec.store(defaultValue));
            if (!comment.isEmpty()) {
                file.comment(path, comment);
            }
            return defaultValue;
        }
        val raw = config.get(path);
        T value;
        try {
            value = codec.load(raw);
        } catch (RuntimeException e) {
            value = null;
        }
        val clamped = value == null ? null : clamp(value);
        val valid = clamped != null && validator.test(clamped);
        val changed = !Objects.equals(clamped, value);
        if (file.strict() && (!valid || changed)) {
            throw InvalidConfigException.of(path, name(), raw);
        }
        if (!valid) {
            log.warn("Invalid value '{}' at '{}' in {} — resetting to {}", raw, path, name(), defaultValue);
            config.set(path, codec.store(defaultValue));
            return defaultValue;
        }
        if (changed) {
            config.set(path, codec.store(clamped));
        }
        return clamped;
    }

    public void set(T value) {
        val checked = clamp(Objects.requireNonNull(value, "null value at '" + path + "'"));
        if (!validator.test(checked)) {
            throw new IllegalArgumentException("Value " + value + " rejected by validator at '" + path + "'");
        }
        file.config().set(path, codec.store(checked));
        file.save();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private T clamp(T value) {
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

    private String name() {
        val p = file.path();
        return p != null ? p.getFileName().toString() : "<memory>";
    }
}
