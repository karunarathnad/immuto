package io.github.karunarathnad.immuto.annotation;

import java.lang.annotation.*;

/**
 * Wraps the result of a mapping method in {@link java.util.Optional}.
 *
 * <p>When placed on a mapper method whose return type is
 * {@code Optional<T>}, the generated implementation returns
 * {@code Optional.empty()} when the source is {@code null} and
 * {@code Optional.of(result)} for a non-null source. This is the
 * recommended way to map nullable record components that should surface
 * as {@code Optional} in the target type.
 *
 * <pre>{@code
 * @RecordMapper
 * public interface ContactMapper {
 *
 *     @NullSafe
 *     Optional<AddressDTO> toAddressDto(AddressEntity entity);
 *
 *     ContactDTO toDto(ContactEntity entity);  // uses toAddressDto internally
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
@Documented
public @interface NullSafe {
}
