package io.github.karunarathnad.immuto.processor.model;

/**
 * Describes how one target record component should be populated.
 *
 * @param targetComponent  name of the target record component
 * @param sourceExpression Java expression yielding the value — already fully resolved
 *                         (may include accessor chains, constants, or custom expressions)
 * @param ignored          when {@code true} the component receives {@code null}
 */
public record MappingModel(
        String targetComponent,
        String sourceExpression,
        boolean ignored
) {

    public static MappingModel direct(String component, String expression) {
        return new MappingModel(component, expression, false);
    }

    public static MappingModel ignored(String component) {
        return new MappingModel(component, "null", true);
    }
}
