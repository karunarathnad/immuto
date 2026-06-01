package io.github.karunarathnad.immuto.annotation;

import java.lang.annotation.*;

/**
 * Marks a default or static method in a mapper interface to be called
 * <em>before</em> the generated mapping logic executes.
 *
 * <p>The method must accept exactly the source type as its only parameter.
 *
 * <pre>{@code
 * @RecordMapper
 * public interface OrderMapper {
 *
 *     @BeforeMapping
 *     default void validate(OrderEntity source) {
 *         Objects.requireNonNull(source.id(), "order id must not be null");
 *     }
 *
 *     OrderDTO toDto(OrderEntity source);
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
@Documented
public @interface BeforeMapping {
}
