package io.github.karunarathnad.immuto.annotation;

import java.lang.annotation.*;

/**
 * Marks an interface as an Immuto record mapper.
 *
 * <p>The annotation processor generates a concrete implementation class named
 * {@code <InterfaceName>Impl} in the same package. The implementation calls the
 * <em>canonical constructor</em> of each target Java Record — never a setter,
 * never a field write.
 *
 * <pre>{@code
 * @RecordMapper
 * public interface OrderMapper {
 *     OrderDTO toDto(OrderEntity entity);
 *     OrderEntity toEntity(OrderDTO dto);
 * }
 * }</pre>
 *
 * <p>Obtain the generated instance via {@link io.github.karunarathnad.immuto.core.Immuto#getMapper(Class)}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
@Documented
public @interface RecordMapper {

    /**
     * Additional {@link io.github.karunarathnad.immuto.core.TypeConverter} implementations
     * the processor should register for use within this mapper's generated methods.
     */
    Class<?>[] uses() default {};

    /**
     * Qualifier name used when multiple mappers handle the same source/target pair.
     */
    String named() default "";

    /**
     * When {@code true} the processor emits a warning for unmapped target record
     * components instead of failing the build.
     */
    boolean warnOnUnmappedTargetComponents() default false;
}
