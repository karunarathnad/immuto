package io.github.karunarathnad.immuto.processor.model;

import java.util.List;

/**
 * Represents one mapping method that the processor will implement.
 *
 * @param methodName               the interface method name to implement
 * @param sourceParamName          the source parameter variable name in the generated method
 * @param sourceTypeFqn            fully-qualified source type (must be a record or null-passthrough)
 * @param targetTypeFqn            fully-qualified target type (must be a record)
 * @param targetSimpleName         simple class name of the target type
 * @param componentMappings        ordered list of per-component mapping decisions
 * @param beforeMappingMethodName  exact name of the @BeforeMapping method to call, or null if none
 * @param afterMappingMethodName   exact name of the @AfterMapping method to call, or null if none
 * @param isNullSafe               whether the return type is Optional-wrapped
 */
public record MapperMethodModel(
        String methodName,
        String sourceParamName,
        String sourceTypeFqn,
        String targetTypeFqn,
        String targetSimpleName,
        List<MappingModel> componentMappings,
        String beforeMappingMethodName,
        String afterMappingMethodName,
        boolean isNullSafe
) {
    public boolean hasBeforeMapping() { return beforeMappingMethodName != null; }
    public boolean hasAfterMapping()  { return afterMappingMethodName  != null; }
}
