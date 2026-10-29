package team.terrafirmagreg.konf.value;

import java.util.function.Consumer;
import java.util.function.Predicate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;
import team.terrafirmagreg.konf.value.Values.Backend;

@Getter
@Accessors(fluent = true)
public class ValueBuilder<T> implements ConfigValue<T> {

    @Getter(AccessLevel.NONE)
    private final Declarer<T> declarer;

    @Getter(AccessLevel.NONE)
    private final Backend backend;

    private final String path;
    private final T defaultValue;
    private T min;
    private T max;
    private String comment = "";
    private Predicate<T> validator = v -> true;
    private Consumer<T> onChange = v -> {};
    private String mergeBy;

    private ConfigValue<T> entry;

    public ValueBuilder(Declarer<T> declarer, Backend backend, String path, T def) {
        this.declarer = declarer;
        this.backend = backend;
        this.path = path;
        this.defaultValue = java.util.Objects.requireNonNull(def, "null default at '" + path + "'");
    }

    public ValueBuilder<T> range(T min, T max) {
        if (!(min instanceof Comparable) || !(max instanceof Comparable)) {
            throw new IllegalArgumentException("range() requires Comparable bounds at '" + path + "'");
        }
        this.min = min;
        this.max = max;
        return this;
    }

    public ValueBuilder<T> comment(String... lines) {
        this.comment = String.join("\n", lines);
        return this;
    }

    public ValueBuilder<T> validate(Predicate<T> validator) {
        this.validator = validator;
        return this;
    }

    public ValueBuilder<T> mergeBy(String nameKey) {
        this.mergeBy = nameKey;
        return this;
    }

    public ValueBuilder<T> onChange(Consumer<T> listener) {
        this.onChange = listener;
        return this;
    }

    public String comment() {
        return comment;
    }

    @Override
    public T get() {
        return entry().get();
    }

    @Override
    public void set(T value) {
        entry().set(value);
    }

    public ConfigValue<T> entry() {
        if (entry == null) {
            entry = declarer.declare(backend, this);
        }
        return entry;
    }

    public void fireChange() {
        onChange.accept(get());
    }

    public interface Declarer<T> {
        ConfigValue<T> declare(Backend backend, ValueBuilder<T> builder);
    }

    public static final class Real extends ValueBuilder<Double> {

        public Real(Declarer<Double> declarer, Backend backend, String path, Double def) {
            super(declarer, backend, path, def);
        }

        public Real range(double min, double max) {
            super.range(min, max);
            return this;
        }

        @Override
        public Real comment(String... lines) {
            super.comment(lines);
            return this;
        }

        @Override
        public Real validate(Predicate<Double> validator) {
            super.validate(validator);
            return this;
        }

        @Override
        public Real onChange(Consumer<Double> listener) {
            super.onChange(listener);
            return this;
        }
    }

    public static final class Integral extends ValueBuilder<Long> {

        public Integral(Declarer<Long> declarer, Backend backend, String path, Long def) {
            super(declarer, backend, path, def);
        }

        public Integral range(long min, long max) {
            super.range(min, max);
            return this;
        }

        @Override
        public Integral comment(String... lines) {
            super.comment(lines);
            return this;
        }

        @Override
        public Integral validate(Predicate<Long> validator) {
            super.validate(validator);
            return this;
        }

        @Override
        public Integral onChange(Consumer<Long> listener) {
            super.onChange(listener);
            return this;
        }
    }
}
