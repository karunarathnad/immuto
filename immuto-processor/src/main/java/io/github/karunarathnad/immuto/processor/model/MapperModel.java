package io.github.karunarathnad.immuto.processor.model;

import java.util.List;

/**
 * Top-level model representing one {@code @RecordMapper}-annotated interface
 * and all its mapping methods.
 *
 * @param packageName        the package where the impl class will be generated
 * @param interfaceSimpleName simple name of the mapper interface (e.g., {@code OrderMapper})
 * @param implClassName      simple name of the generated class (e.g., {@code OrderMapperImpl})
 * @param methods            ordered list of methods to implement
 */
public record MapperModel(
        String packageName,
        String interfaceSimpleName,
        String implClassName,
        List<MapperMethodModel> methods
) {}
