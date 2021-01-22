package at.reisishot.mapping;


import java.beans.FeatureDescriptor;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

public class MappingUtils {
    private static final Map<Class<?>, Map<String, PropertyDescriptor>> propertyDescriptorCache = new HashMap<>();

    private static final Map<AbstractMap.SimpleImmutableEntry<Class<?>, Class<?>>, Function<Object, Object>> objectConverters = new HashMap<>();

    static {
        objectConverters.put(pairOf(String.class, Integer.TYPE), e -> Integer.parseInt((String) e));
        objectConverters.put(pairOf(BigDecimal.class, String.class), Object::toString);
    }


    public static <F, T> T map(F from, Class<T> target, Collection<MappingCommand> mappings) {
        try {
            return performMapping(
                    requireNonNull(from, "Source object is required"),
                    requireNonNull(target, "Target class is required"),
                    requireNonNull(mappings, "Mappings is required. A empty collection is valid")
            );
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static <F, T> T performMapping(
            F from,
            Class<T> target,
            Collection<MappingCommand> mappings
    ) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        final Constructor<T> constructor = requireNonNull(target.getConstructor(), "No zero-arg constructor found");
        final T toReturn = constructor.newInstance();

        for (MappingCommand mapping : mappings) {
            String fromKey = mapping.getSource();
            String toKey = mapping.getTarget();
            performMapping(from, fromKey, toReturn, toKey);
        }

        return toReturn;
    }

    private static <F, T> void performMapping(
            F from,
            String fromKey,
            T to,
            String toKey
    ) throws InvocationTargetException, IllegalAccessException, NoSuchMethodException, InstantiationException {
        final Object value = readValue(from, fromKey);
        if (value == null)
            return;
        setValue(to, toKey, value);
    }

    private static <T> void setValue(
            T to,
            String toKey,
            Object value
    ) throws InvocationTargetException, IllegalAccessException, NoSuchMethodException, InstantiationException {
        final Object sourceData = findObject(to, toKey);

        final String propertyName = getAfterLastDot(toKey);
        Method writeMethod = getPropertyDecriptor(
                sourceData,
                propertyName
        )
                .getWriteMethod();

        requireNonNull(writeMethod, "No setter found for: " + propertyName)
                .invoke(sourceData, computeMatchingValue(value, writeMethod));
    }


    private static Object computeMatchingValue(Object value, Method writeMethod) {
        Class<?> aClass = getValueClass(value);
        Class<?> setterType = writeMethod.getParameterTypes()[0];
        if (setterType.isAssignableFrom(aClass))
            return value;
        return findConverter(aClass, setterType)
                .apply(value);
    }

    private static Function<Object, Object> findConverter(Class<?> valueClass, Class<?> setterClass) {
        AbstractMap.SimpleImmutableEntry<? extends Class<?>, ? extends Class<?>> key = pairOf(valueClass, setterClass);
        Function<Object, Object> converter = objectConverters.get(key);
        if (converter == null)
            throw new IllegalStateException("No converter found for: " + key);
        return converter;
    }

    private static Class<?> getValueClass(Object value) {
        Class<?> aClass = Object.class;
        if (value != null) {
            Class<?> oClass = value.getClass();
            if (oClass != null) aClass = oClass;
        }
        return aClass;
    }

    private static <F> Object readValue(
            F from,
            String fromKey
    ) throws InvocationTargetException, IllegalAccessException, NoSuchMethodException, InstantiationException {
        Object o = findObject(from, fromKey);
        final String propertyName = getAfterLastDot(fromKey);
        final Method readMethod = getPropertyDecriptor(o, propertyName)
                .getReadMethod();

        return requireNonNull(readMethod, "No getter found for >" + propertyName + "<")
                .invoke(o);
    }

    private static String getAfterLastDot(String s) {
        final int idx = s.lastIndexOf('.');
        if (idx == -1)
            return s;
        return s.substring(idx + 1);
    }

    private static Object findObject(
            Object current,
            String accessKey
    ) throws InvocationTargetException, IllegalAccessException, NoSuchMethodException, InstantiationException {
        String cur = substringBefore(accessKey, '.');
        if (Objects.equals(cur, accessKey))
            return current;
        PropertyDescriptor stepPd = getPropertyDecriptor(current, cur);
        Method readMethod = requireNonNull(stepPd.getReadMethod(), "Getter not found for >" + cur + "<");
        Object stepValue = readMethod.invoke(current);
        if (stepValue == null) {
            stepValue = readMethod.getReturnType()
                    .getConstructor()
                    .newInstance();
            final Method writeMethod = requireNonNull(stepPd.getWriteMethod(), "Setter not found for >" + cur + "<");
            writeMethod.invoke(current, stepValue);
        }
        return findObject(stepValue, substringAfter(accessKey, '.'));
    }

    private static PropertyDescriptor getPropertyDecriptor(Object target, String propertyName) {
        return getPropertyDecriptor(target.getClass(), propertyName);
    }

    private static PropertyDescriptor getPropertyDecriptor(Class<?> target, String propertyName) {
        Map<String, PropertyDescriptor> properties = propertyDescriptorCache.computeIfAbsent(target, MappingUtils::computePropertyDescriptors);
        final PropertyDescriptor pd = properties.get(propertyName);
        if (pd == null)
            throw new IllegalStateException("The class >" + target.getSimpleName() + "< does not contain a property identified by >" + propertyName + "<");
        return pd;
    }

    private static Map<String, PropertyDescriptor> computePropertyDescriptors(Class<?> target) {
        try {
            return Arrays.stream(Introspector.getBeanInfo(target).getPropertyDescriptors())
                    .collect(Collectors.toMap(FeatureDescriptor::getName, e -> e));
        } catch (IntrospectionException e) {
            throw new RuntimeException(e);
        }
    }

    private static <K, V> AbstractMap.SimpleImmutableEntry<K, V> pairOf(K key, V value) {
        return new AbstractMap.SimpleImmutableEntry<>(key, value);
    }

    private static String substringAfter(String s, char c) {
        return s.substring(s.indexOf(c) + 1);
    }

    private static String substringBefore(String s, char c) {
        final int endIndex = s.indexOf(c);
        if (endIndex == -1)
            return s;
        return s.substring(0, endIndex);
    }

}
