package io.github.karunarathnad.immuto.core;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Runtime helper that inspects Java record types and instantiates them
 * via their canonical constructor.
 *
 * <p>Component metadata is cached after the first access, so repeated
 * introspection of the same record type is inexpensive.
 *
 * <p>This class is used by the fluent API and as a last-resort fallback.
 * Processor-generated mappers do <em>not</em> use it — they call canonical
 * constructors directly via generated source code.
 */
public final class RecordIntrospector {

    private static final Map<Class<?>, List<ImmutoRecordComponent>> COMPONENT_CACHE =
            new ConcurrentHashMap<>();

    private RecordIntrospector() {}

    /**
     * Returns the ordered list of record components for {@code recordClass}.
     * Results are cached.
     */
    public static List<ImmutoRecordComponent> components(Class<?> recordClass) {
        return COMPONENT_CACHE.computeIfAbsent(recordClass, ImmutoRecordComponent::of);
    }

    /**
     * Returns the components as a name→component map.
     */
    public static Map<String, ImmutoRecordComponent> componentMap(Class<?> recordClass) {
        return components(recordClass).stream()
                .collect(Collectors.toUnmodifiableMap(ImmutoRecordComponent::name, Function.identity()));
    }

    /**
     * Instantiates a record via its canonical constructor.
     *
     * @param recordClass   the record type to create
     * @param args          constructor arguments in canonical constructor order
     * @param <T>           the record type
     * @return a new record instance
     * @throws MappingException if construction fails
     */
    @SuppressWarnings("unchecked")
    public static <T> T instantiate(Class<T> recordClass, Object[] args) {
        List<ImmutoRecordComponent> components = components(recordClass);
        if (args.length != components.size()) {
            throw new MappingException(String.format(
                    "Canonical constructor for %s expects %d arguments but %d were supplied",
                    recordClass.getName(), components.size(), args.length));
        }

        Class<?>[] paramTypes = components.stream()
                .map(ImmutoRecordComponent::type)
                .toArray(Class[]::new);
        try {
            Constructor<T> ctor = recordClass.getDeclaredConstructor(paramTypes);
            ctor.setAccessible(true);
            return ctor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new MappingException(
                    "Failed to instantiate record " + recordClass.getName()
                            + " with args " + Arrays.toString(args), e);
        }
    }

    /**
     * Copies all components from {@code source} into a new instance of {@code targetClass},
     * matching components by name. Components present in {@code targetClass} but absent
     * from {@code source} receive {@code null}.
     *
     * @throws MappingException if target is not a record or construction fails
     */
    public static <T> T shallowCopy(Object source, Class<T> targetClass) {
        Map<String, ImmutoRecordComponent> sourceMap = componentMap(source.getClass());
        List<ImmutoRecordComponent> targetComponents = components(targetClass);

        Object[] args = new Object[targetComponents.size()];
        for (ImmutoRecordComponent target : targetComponents) {
            ImmutoRecordComponent src = sourceMap.get(target.name());
            args[target.index()] = (src != null) ? src.read(source) : null;
        }
        return instantiate(targetClass, args);
    }
}
