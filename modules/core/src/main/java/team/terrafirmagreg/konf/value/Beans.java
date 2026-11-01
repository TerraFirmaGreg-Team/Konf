package team.terrafirmagreg.konf.value;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.serde.ObjectDeserializer;
import com.electronwill.nightconfig.core.serde.ObjectSerializer;
import java.util.List;
import java.util.function.Supplier;
import lombok.experimental.UtilityClass;
import lombok.val;

@UtilityClass
class Beans {

    private static final ObjectSerializer SERIALIZER =
            ObjectSerializer.builder().build();
    private static final ObjectDeserializer DESERIALIZER =
            ObjectDeserializer.builder().build();

    static <T> ValueCodec<T> codec(Supplier<T> ctor) {
        return new ValueCodec<>() {
            @Override
            public Object store(T value) {
                return SERIALIZER.serializeFields(value, CommentedConfig::inMemory);
            }

            @Override
            @SuppressWarnings("unchecked")
            public T load(Object raw) {
                if (!(raw instanceof UnmodifiableConfig cfg)) {
                    return null;
                }
                T instance = ctor.get();
                val merged = CommentedConfig.inMemory();
                overlayInto(merged, SERIALIZER.serializeFields(instance, CommentedConfig::inMemory));
                overlayInto(merged, cfg);
                if (instance.getClass().isRecord()) {
                    return toRecord(merged, (Class<T>) instance.getClass());
                }
                DESERIALIZER.deserializeFields(merged, instance);
                return instance;
            }
        };
    }

    static <R extends Record> ValueCodec<R> codec(Class<R> type) {
        return new ValueCodec<>() {
            @Override
            public Object store(R value) {
                return SERIALIZER.serializeFields(value, CommentedConfig::inMemory);
            }

            @Override
            public R load(Object raw) {
                return raw instanceof UnmodifiableConfig cfg ? toRecord(cfg, type) : null;
            }
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> T toRecord(UnmodifiableConfig cfg, Class<T> type) {
        return (T) DESERIALIZER
                .deserializeToCollection(List.of(cfg), List.class, type)
                .get(0);
    }

    private static void overlayInto(CommentedConfig dst, UnmodifiableConfig src) {
        for (val e : src.entrySet()) {
            if (e.getValue() instanceof UnmodifiableConfig sub
                    && dst.get(List.of(e.getKey())) instanceof CommentedConfig subDst) {
                overlayInto(subDst, sub);
            } else {
                dst.set(List.of(e.getKey()), e.getValue());
            }
        }
    }
}
