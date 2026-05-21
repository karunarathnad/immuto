package io.github.karunarathnad.immuto.annotation;

import java.lang.annotation.*;

/**
 * Convenience meta-annotation that instructs the processor to generate
 * <em>both</em> directions of a mapping between two record types on a
 * single interface method declaration.
 *
 * <p>Annotating a method with {@code @BidirectionalMapper} is equivalent
 * to writing two methods and annotating the second with
 * {@link InheritInverseConfiguration}. The reverse method name is derived
 * by prepending the {@link #reversePrefix()} to the original method name.
 *
 * <pre>{@code
 * @RecordMapper
 * public interface ItemMapper {
 *
 *     @BidirectionalMapper           // generates toDto + fromDto
 *     ItemDTO toDto(ItemEntity entity);
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
@Documented
public @interface BidirectionalMapper {

    /**
     * Prefix for the generated reverse method name.
     * Default produces {@code fromXxx} when the forward method is {@code toXxx}.
     */
    String reversePrefix() default "from";

    /**
     * Suffix to strip from the forward method name before applying
     * {@link #reversePrefix()}. Defaults to {@code "to"} so that
     * {@code toDto} produces {@code fromDto}.
     */
    String stripPrefix() default "to";
}
