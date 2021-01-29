package at.reisishot.mapping;


import java.beans.FeatureDescriptor;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

public class MappingUtils {
    private static final Map<Class<?>, Map<String, PropertyDescriptor>> propertyDescriptorCache = new HashMap<>();

    private static final Map<Class<?>, Supplier<?>> interfaceCreator = new HashMap<>();

    private static final Map<AbstractMap.SimpleImmutableEntry<Class<?>, Class<?>>, Function<Object, Object>> objectConverters = new HashMap<>();


    private static final Pattern arrayListPattern = Pattern.compile("\\[(?<idx>.+)]");

    static {
        objectConverters.put(pairOf(String.class, Integer.TYPE), e -> Integer.parseInt((String) e));
        objectConverters.put(pairOf(Integer.class, String.class), String::valueOf);
        objectConverters.put(pairOf(BigDecimal.class, String.class), Object::toString);

        interfaceCreator.put(List.class, ArrayList::new);
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
        mapArrayOrList(from, fromKey, to, toKey);
    }

    private static <F, T> void mapArrayOrList(
            final F from,
            final String fromKey,
            final T to,
            final String toKey
    ) throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        final String[] fromSplitted = arrayListPattern.split(fromKey);
        final String[] toSplitted = arrayListPattern.split(toKey);

        mapArrayOrListInternal(from, fromKey, fromSplitted, to, toKey, toSplitted);
    }

    private static <F, T> void mapArrayOrListInternal(
            final F from,
            final String fromKey,
            final String[] fromSplitted,
            final T to,
            final String toKey,
            final String[] toSplitted
    ) throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        if (fromSplitted.length == 0 || toSplitted.length == 0) {
            return;
        }
        final String curFrom = fromSplitted[0];
        final String curTo = toSplitted[0];

        final Object newFrom = findObject(from, curFrom);
        final Object newTo = findObject(to, curTo);

        final String[] newFromSplitted = copyExceptFirstElement(fromSplitted);
        final String[] newToSplitted = copyExceptFirstElement(toSplitted);

        final String newFromKeyTmp = substringAfter(fromKey, curFrom);
        final String newToKeyTmp = substringAfter(toKey, curTo);

        final Matcher fromMatcher = arrayListPattern.matcher(newFromKeyTmp);
        final Matcher toMatcher = arrayListPattern.matcher(newToKeyTmp);

        final boolean arrayFound = fromMatcher.find();
        if (!(arrayFound == toMatcher.find()))
            throw new IllegalArgumentException("No equal loop condition");

        if (!arrayFound) {
            mapSimpleValue(from, curFrom, to, curTo);
            return;
        }

        final String fromVar = fromMatcher.group(1);
        final String toVar = toMatcher.group(1);
        if (!fromVar.equals(toVar))
            throw new IllegalAccessException("Variables are not the same");

        final Object fromLa = findObject(newFrom, curFrom);
        final List<Object> fromList = ((List<Object>) readValue(fromLa, curFrom));

        if (fromList == null || fromList.isEmpty())
            return;

        final Object toLa = findObject(newTo, curTo);

        final List<Object> toList = ((List<Object>) createObjectIfNotExists(toLa, curTo));

        final Object targetParentObject = findObject(to, substringBefore(curTo, '.'));

        final Class<?> actualTargetTypeArgument = (Class<?>) ((ParameterizedType) getPropertyDecriptor(targetParentObject, substringAfterLast(curTo, '.'))
                .getReadMethod()
                .getGenericReturnType())
                .getActualTypeArguments()[0];

        for (int i = 0; i < fromList.size(); i++) {
            final Object iFrom = fromList.get(i);
            Object iTo;
            if (toList.size() - 1 >= i) {
                iTo = toList.get(i);
                mapArrayOrListInternal(iFrom, substringAfter(newFromKeyTmp, fromMatcher.group(0)), newFromSplitted, iTo, substringAfter(newToKeyTmp, toMatcher.group(0)), newToSplitted);
                return;
            } else {
                if (toSplitted.length > 1) {
                    //With subobjects
                    iTo = createObject(actualTargetTypeArgument);
                    toList.add(iTo);
                    mapArrayOrListInternal(iFrom, substringAfter(newFromKeyTmp, fromMatcher.group(0)), newFromSplitted, iTo, substringAfter(newToKeyTmp, toMatcher.group(0)), newToSplitted);
                } else {
                    // with matching values
                    final Object value;
                    final String arrayIndex = "[" + fromVar + "].";
                    if (arrayIndex.startsWith(newFromKeyTmp)) {
                        value = iFrom;
                    } else {
                        value = readValue(iFrom, substringAfter(newFromKeyTmp, arrayIndex));
                    }
                    toList.add(computeMatchingValue(value, actualTargetTypeArgument));
                }

            }

        }
    }

    private static String[] copyExceptFirstElement(final String[] array) {
        return Arrays.copyOfRange(array, 1, array.length);
    }

    private static <F, T> void mapSimpleValue(
            final F from,
            final String fromKey,
            final T to,
            final String toKey
    ) throws InvocationTargetException, IllegalAccessException, NoSuchMethodException, InstantiationException {
        final Object value = readValue(from, fromKey);
        if (value == null)
            return;
        writeValue(to, toKey, value);
    }

    private static <T> void writeValue(
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
        Class<?> setterType = writeMethod.getParameterTypes()[0];
        return computeMatchingValue(value, setterType);
    }

    private static Object computeMatchingValue(final Object value, final Class<?> targetType) {
        Class<?> aClass = getValueClass(value);
        if (targetType.isAssignableFrom(aClass))
            return value;
        return findConverter(aClass, targetType)
                .apply(value);
    }

    private static Function<Object, Object> findConverter(Class<?> valueClass, Class<?> setterClass) {
        AbstractMap.SimpleImmutableEntry<? extends Class<?>, ? extends Class<?>> key = pairOf(valueClass, setterClass);
        Function<Object, Object> converter = objectConverters.get(key);
        if (converter == null)
            throw new IllegalStateException("No converter found for: " + key.getKey() + " -> " + key.getValue());
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
            final Object current,
            final String accessKey
    ) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException, InstantiationException {
        String cur = substringBefore(accessKey, '.');
        if (Objects.equals(cur, accessKey)) {
            return current;
        }
        Object stepValue = createObjectIfNotExists(current, cur);
        return findObject(stepValue, substringAfter(accessKey, '.'));

    }

    private static Object createObjectIfNotExists(
            final Object o,
            final String property
    ) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException, InstantiationException {
        PropertyDescriptor stepPd = getPropertyDecriptor(o, property);
        Method readMethod = requireNonNull(stepPd.getReadMethod(), "Getter not found for >" + property + "<");
        Object stepValue = readMethod.invoke(o);
        if (stepValue == null) {
            stepValue = createObject(readMethod.getReturnType());
            final Method writeMethod = requireNonNull(stepPd.getWriteMethod(), "Setter not found for >" + property + "<");
            writeMethod.invoke(o, stepValue);
        }
        return stepValue;
    }

    private static Object createObject(final Class<?> aClass) throws NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        final Supplier<?> supplier = interfaceCreator.get(aClass);
        if (supplier != null) {
            return supplier.get();
        } else {
            final Constructor<?> constructor = aClass.getConstructor();
            return requireNonNull(constructor, "No zero-arg constructor found")
                    .newInstance();
        }
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

    private static String substringAfterLast(String s, char c) {
        return s.substring(s.lastIndexOf(c) + 1);
    }

    private static String substringAfter(String s, String c) {
        return s.substring(s.indexOf(c) + c.length());
    }

    private static String substringBefore(String s, char c) {
        final int endIndex = s.indexOf(c);
        if (endIndex == -1)
            return s;
        return s.substring(0, endIndex);
    }

    private static String substringBefore(String s, String c) {
        final int endIndex = s.indexOf(c);
        if (endIndex == -1)
            return s;
        return s.substring(0, endIndex + c.length());
    }

    private static String substringBeforeLast(String s, String c) {
        final int endIndex = s.lastIndexOf(c);
        if (endIndex == -1)
            return s;
        return s.substring(0, endIndex);
    }

}
