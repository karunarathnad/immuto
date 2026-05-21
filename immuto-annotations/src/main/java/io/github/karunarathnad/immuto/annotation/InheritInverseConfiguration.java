package io.github.karunarathnad.immuto.annotation;

import java.lang.annotation.*;

/**
 * Inherits the inverse of another mapping method in the same interface.
 *
 * <p>The processor locates the complementary method (reversed source/target types)
 * and generates the inverse automatically. Only 1-to-1 component name mappings or
 * explicit {@link Mapping#source()}/{@link Mapping#target()} pairs can be reversed;
 * expression-based mappings cause a compile-time error.
 *
 * <pre>{@code
 * @RecordMapper
 * public interface ProductMapper {
 *     ProductDTO toDto(ProductEntity entity);
 *
 *     @InheritInverseConfiguration(name = "toDto")
 *     ProductEntity toEntity(ProductDTO dto);
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
@Documented
public @interface InheritInverseConfiguration {

    /**
     * Name of the method whose configuration to invert.
     * Required when the interface contains more than one method mapping
     * between the same pair of types.
     */
    String name() default "";
}
