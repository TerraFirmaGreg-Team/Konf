package team.terrafirmagreg.konf.value;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.experimental.UtilityClass;
import lombok.val;

@UtilityClass
class Beans {

    static <T> ValueCodec<T> codec(Class<T> type) {
        return new ValueCodec<>() {
            @Override
            public Object store(T value) {
                return toConfig(value);
            }

            @Override
            @SuppressWarnings("unchecked")
            public T load(Object raw) {
                return raw instanceof UnmodifiableConfig cfg ? (T) toBean(cfg, type) : null;
            }
        };
    }

    static String camelToSnake(String s) {
        val out = new StringBuilder(s.length() + 4);
        for (char c : s.toCharArray()) {
            if (Character.isUpperCase(c)) {
                out.append('_').append(Character.toLowerCase(c));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static List<Field> beanFields(Class<?> type) {
        val fields = new ArrayList<Field>();
        for (Field f : type.getFields()) {
            if (!Modifier.isStatic(f.getModifiers())) {
                fields.add(f);
            }
        }
        fields.sort(Comparator.comparing(Field::getName));
        return fields;
    }

    private static Config toConfig(Object bean) {
        val cfg = CommentedConfig.inMemory();
        for (Field f : beanFields(bean.getClass())) {
            try {
                if (!f.canAccess(bean)) {
                    f.trySetAccessible();
                }
                val v = store(f.get(bean), f.getGenericType());
                if (v != null) {
                    cfg.set(camelToSnake(f.getName()), v);
                }
            } catch (IllegalAccessException | RuntimeException ignored) {
            }
        }
        return cfg;
    }

    private static Object store(Object value, Type type) {
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> list) {
            val out = new ArrayList<>(list.size());
            Type element = type instanceof ParameterizedType pt ? pt.getActualTypeArguments()[0] : Object.class;
            for (Object item : list) {
                val v = store(item, element);
                if (v != null) {
                    out.add(v);
                }
            }
            return out;
        }
        if (isScalar(value.getClass())) {
            return value;
        }
        return toConfig(value);
    }

    private static Object toBean(UnmodifiableConfig cfg, Type type) {
        Class<?> cls = type instanceof ParameterizedType pt ? (Class<?>) pt.getRawType() : (Class<?>) type;
        if (isScalar(cls)) {
            return null;
        }
        try {
            Object bean = cls.getDeclaredConstructor().newInstance();
            for (Field f : beanFields(cls)) {
                if (!f.canAccess(bean)) {
                    f.trySetAccessible();
                }
                Object raw = cfg.get(camelToSnake(f.getName()));
                if (raw == null) {
                    continue;
                }
                val v = load(raw, f.getType(), f.getGenericType());
                if (v != null) {
                    f.set(bean, v);
                }
            }
            return bean;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static boolean isScalar(Class<?> cls) {
        return cls.isPrimitive()
                || cls == String.class
                || Number.class.isAssignableFrom(cls)
                || cls == Boolean.class
                || cls.isEnum();
    }

    @SuppressWarnings("unchecked")
    private static Object load(Object raw, Class<?> type, Type generic) {
        if (raw instanceof UnmodifiableConfig c) {
            val v = toBean(c, generic);
            return v != null ? v : raw;
        }
        if (raw instanceof Number n) {
            if (type == int.class || type == Integer.class) return n.intValue();
            if (type == long.class || type == Long.class) return n.longValue();
            if (type == double.class || type == Double.class) return n.doubleValue();
            if (type == float.class || type == Float.class) return n.floatValue();
            if (type == byte.class || type == Byte.class) return n.byteValue();
            if (type == short.class || type == Short.class) return n.shortValue();
            return n;
        }
        if (raw instanceof List<?> list) {
            Type element = generic instanceof ParameterizedType pt ? pt.getActualTypeArguments()[0] : String.class;
            val out = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item instanceof UnmodifiableConfig c) {
                    val parsed = toBean(c, element);
                    if (parsed != null) {
                        out.add(parsed);
                    }
                } else {
                    out.add(item);
                }
            }
            return out;
        }
        if (type.isEnum() && raw instanceof String s) {
            return Enum.valueOf((Class<? extends Enum>) type.asSubclass(Enum.class), s);
        }
        return raw;
    }
}
