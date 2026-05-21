package io.github.karunarathnad.immuto.annotation;

import java.lang.annotation.*;

/**
 * Qualifies a {@link io.github.karunarathnad.immuto.core.TypeConverter} implementation
 * or a mapper interface so it can be referenced unambiguously.
 *
 * <pre>{@code
 * @Named("isoDate")
 * public class IsoDateConverter implements TypeConverter<LocalDate, String> { ... }
 *
 * // and in the mapper:
 * @Mapping(target = "deliveryDate", qualifiedBy = "isoDate")
 * }</pre>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.CLASS)
@Documented
public @interface Named {
    /** The qualifier value. */
    String value();
}
