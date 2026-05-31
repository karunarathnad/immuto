package io.github.karunarathnad.immuto.core;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Immutable descriptor of a single Java record component, enriched with
 * convenient accessors used by the fluent runtime API.
 *
 * @param name       the component name (matches the accessor method name)
 * @param type       the component's declared type
 * @param accessor   the zero-argument accessor method
 * @param index      position in the canonical constructor parameter list
 */
public record ImmutoRecordComponent(
        String name,
        Class<?> type,
        Method accessor,
        int index
) {

    /**
     * Reads all record components from {@code recordClass} and returns them
     * as an ordered, immutable list matching the canonical constructor order.
     *
     * @throws MappingException if {@code recordClass} is not a record
     */
    public static List<ImmutoRecordComponent> of(Class<?> recordClass) {
        if (!recordClass.isRecord()) {
            throw new MappingException(recordClass.getName() + " is not a Java record");
        }
        java.lang.reflect.RecordComponent[] components = recordClass.getRecordComponents();
        ImmutoRecordComponent[] result = new ImmutoRecordComponent[components.length];
        for (int i = 0; i < components.length; i++) {
            Method accessor = components[i].getAccessor();
            accessor.setAccessible(true);
            result[i] = new ImmutoRecordComponent(
                    components[i].getName(),
                    components[i].getType(),
                    accessor,
                    i
            );
        }
        return List.of(result);
    }

    /**
     * Reads the component value from a record instance.
     *
     * @throws MappingException if the accessor invocation fails
     */
    public Object read(Object record) {
        try {
            return accessor.invoke(record);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new MappingException(
                    "Failed to read component '" + name + "' from " + record.getClass().getName(), cause);
        } catch (ReflectiveOperationException e) {
            throw new MappingException(
                    "Failed to read component '" + name + "' from " + record.getClass().getName(), e);
        }
    }
}
