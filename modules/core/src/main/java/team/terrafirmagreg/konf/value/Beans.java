package team.terrafirmagreg.konf.value;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.NullObject;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.serde.ObjectDeserializer;
import com.electronwill.nightconfig.core.serde.ObjectSerializer;
import com.electronwill.nightconfig.core.serde.annotations.SerdeKey;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.function.Consumer;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.jspecify.annotations.Nullable;

@UtilityClass
class Beans {

    private static final ObjectSerializer SERIALIZER = ObjectSerializer.standard();
    private static final ObjectDeserializer DESERIALIZER = ObjectDeserializer.standard();

    private static final @Nullable Method TO_RECORD = toRecord();

    static <T> ValueCodec<T> codec(Class<T> type) {
        if (!type.isRecord()) {
            try {
                type.getDeclaredConstructor();
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException(
                        "bean type needs a no-arg constructor or be a record: " + type.getName());
            }
        }
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
                if (type.isRecord()) {
                    try {
                        return TO_RECORD == null ? null : (T) TO_RECORD.invoke(DESERIALIZER, cfg, type);
                    } catch (ReflectiveOperationException e) {
                        return null;
                    }
                }
                val merged = CommentedConfig.inMemory();
                overlayInto(merged, SERIALIZER.serializeFields(newInstance(type), CommentedConfig::inMemory));
                overlayInto(merged, cfg);
                forEachField(type, f -> {
                    if (!Modifier.isStatic(f.getModifiers()) && merged.get(key(f)) == null) {
                        merged.set(key(f), NullObject.NULL_OBJECT);
                    }
                });
                return (T) DESERIALIZER.deserializeFields(merged, () -> newInstance(type));
            }
        };
    }

    private static void overlayInto(CommentedConfig dst, UnmodifiableConfig src) {
        for (val e : src.entrySet()) {
            if (e.getValue() instanceof UnmodifiableConfig sub
                    && dst.get(e.getKey()) instanceof CommentedConfig subDst) {
                overlayInto(subDst, sub);
            } else {
                dst.set(e.getKey(), e.getValue());
            }
        }
    }

    private static String key(Field f) {
        val k = f.getAnnotation(SerdeKey.class);
        return k == null ? f.getName() : k.value();
    }

    private static void forEachField(Class<?> type, Consumer<Field> action) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (val f : c.getDeclaredFields()) {
                action.accept(f);
            }
        }
    }

    private static <T> T newInstance(Class<T> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot instantiate " + type.getName(), e);
        }
    }

    // deserializeToRecord lives only in the MR-jar's Java-17 class — resolved at runtime, invisible to javac
    private static @Nullable Method toRecord() {
        try {
            return ObjectDeserializer.class.getMethod("deserializeToRecord", UnmodifiableConfig.class, Class.class);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
