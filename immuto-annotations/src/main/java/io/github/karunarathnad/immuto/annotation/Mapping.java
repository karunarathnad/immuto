package io.github.karunarathnad.immuto.annotation;

import java.lang.annotation.*;

/**
 * Configures how a single target record component is populated.
 *
 * <p>Place this annotation on a mapper method to override or extend
 * the default by-name component mapping:
 *
 * <pre>{@code
 * @RecordMapper
 * public interface PersonMapper {
 *
 *     @Mapping(target = "fullName",
 *              expression = "java(source.firstName() + \" \" + source.lastName())")
 *     @Mapping(target = "active", constant = "true")
 *     PersonDTO toDto(PersonEntity source);
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
@Documented
@Repeatable(Mappings.class)
public @interface Mapping {

    /** Name of the target record component. Must match the component name exactly. */
    String target();

    /**
     * Name of the source accessor to read from. Defaults to the same name as
     * {@link #target()}. Supports dot-notation for nested access: {@code "address.city"}.
     */
    String source() default "";

    /**
     * A Java expression evaluated inside the generated method body.
     * The source parameter is available as {@code source}.
     * Example: {@code "java(source.firstName() + \" \" + source.lastName())"}
     * <p>Mutually exclusive with {@link #source()}, {@link #constant()}, and {@link #ignore()}.
     */
    String expression() default "";

    /**
     * A literal constant value. The string is parsed to the target component's
     * type at code-generation time.
     * <p>Mutually exclusive with {@link #source()}, {@link #expression()}, and {@link #ignore()}.
     */
    String constant() default "";

    /**
     * When {@code true} the target component receives {@code null} (or a zero-value)
     * and is excluded from source analysis.
     */
    boolean ignore() default false;

    /**
     * Qualifier matching {@link Named#value()} to select a specific
     * {@link io.github.karunarathnad.immuto.core.TypeConverter} for this component.
     */
    String qualifiedBy() default "";

    /**
     * When {@code true} and the resolved source value is {@code null},
     * the type's default (null / 0 / false) is used rather than propagating null.
     */
    boolean defaultForNull() default false;

    /**
     * Java expression supplying a default when the source value is {@code null}.
     * Requires {@link #defaultForNull()} to be {@code true}.
     */
    String defaultExpression() default "";
}
