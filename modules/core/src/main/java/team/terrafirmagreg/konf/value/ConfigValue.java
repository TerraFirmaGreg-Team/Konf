package team.terrafirmagreg.konf.value;

import java.util.function.Consumer;
import java.util.function.Supplier;

public interface ConfigValue<T> extends Supplier<T> {

    static <T> ConfigValue<T> of(T def, Supplier<T> getter, Consumer<T> setter) {
        return new ConfigValue<>() {
            @Override
            public T defaultValue() {
                return def;
            }

            @Override
            public T get() {
                return getter.get();
            }

            @Override
            public void set(T value) {
                setter.accept(value);
            }
        };
    }

    T defaultValue();

    @Override
    T get();

    void set(T value);
}
