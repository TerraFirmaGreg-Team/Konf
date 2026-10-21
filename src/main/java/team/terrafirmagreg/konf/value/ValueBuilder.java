package team.terrafirmagreg.konf.value;

import java.util.function.Consumer;
import java.util.function.Predicate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;
import team.terrafirmagreg.konf.value.Values.Backend;

/**
 * Fluent value spec: {@code cfg.value("server.port", 25565).range(1, 65535).comment("...")}. Materializes lazily on
 * first {@code get()}/{@code set()} or {@code ConfigFile.sync()}.
 */
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
        this.defaultValue = def;
    }

    public ValueBuilder<T> range(T min, T max) {
        this.min = min;
        this.max = max;
        return this;
    }

    /** Multi-line: lines are joined with '\n'. Only rendered by commented formats. */
    public ValueBuilder<T> comment(String... lines) {
        this.comment = String.join("\n", lines);
        return this;
    }

    /** Values failing the predicate are treated as invalid: reset to default on read, rejected on set. */
    public ValueBuilder<T> validate(Predicate<T> validator) {
        this.validator = validator;
        return this;
    }

    /**
     * For list-of-tables values: on read, default elements whose {@code nameKey} field is missing from the file's list
     * are appended (per-name upgrade merge). Lists whose elements lack {@code nameKey} are left untouched.
     */
    public ValueBuilder<T> mergeBy(String nameKey) {
        this.mergeBy = nameKey;
        return this;
    }

    /** Fired with the fresh value each time the owning file reloads (manual or watched). */
    public ValueBuilder<T> onChange(Consumer<T> listener) {
        this.onChange = listener;
        return this;
    }

    /** Spec accessor — the fluent {@link #comment(String...)} setter shares the name. */
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

    /** Materializes the backend entry; called lazily and from {@code ConfigFile.sync()}. */
    public ConfigValue<T> entry() {
        if (entry == null) {
            entry = declarer.declare(backend, this);
        }
        return entry;
    }

    /** Fires {@link #onChange} with the current value — called by {@code ConfigFile} on reloads. */
    public void fireChange() {
        onChange.accept(get());
    }

    public interface Declarer<T> {
        ConfigValue<T> declare(Backend backend, ValueBuilder<T> builder);
    }

    /** Double flavour: primitive {@code range(double, double)} so int literals widen without boxing. */
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

    /** Long flavour: primitive {@code range(long, long)} so int literals widen without boxing. */
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
