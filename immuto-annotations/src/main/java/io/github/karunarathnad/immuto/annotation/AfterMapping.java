package io.github.karunarathnad.immuto.annotation;

import java.lang.annotation.*;

/**
 * Marks a default or static method in a mapper interface to be called
 * <em>after</em> the generated mapping logic executes.
 *
 * <p>The method receives both source and the newly-constructed target record.
 * Because records are immutable the method may not mutate the target;
 * its primary use cases are validation, audit logging, and side effects.
 *
 * <pre>{@code
 * @RecordMapper
 * public interface OrderMapper {
 *
 *     @AfterMapping
 *     default void audit(OrderEntity source, OrderDTO target) {
 *         AuditLog.record("mapped order " + target.id());
 *     }
 *
 *     OrderDTO toDto(OrderEntity source);
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
@Documented
public @interface AfterMapping {
}
