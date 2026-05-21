package io.github.karunarathnad.immuto.core;

/**
 * SPI for custom type conversions used during record mapping.
 *
 * <p>Implement this interface and register it via
 * {@link io.github.karunarathnad.immuto.annotation.RecordMapper#uses()} to
 * convert a source component type {@code S} into a target component type {@code T}.
 *
 * <pre>{@code
 * public class MoneyConverter implements TypeConverter<BigDecimal, MoneyDTO> {
 *     @Override
 *     public MoneyDTO convert(BigDecimal source, MappingContext ctx) {
 *         return source == null ? null : new MoneyDTO(source, "USD");
 *     }
 * }
 * }</pre>
 *
 * <p>A converter is stateless by convention; the same instance may be shared
 * across threads.
 *
 * @param <S> source type
 * @param <T> target type
 */
@FunctionalInterface
public interface TypeConverter<S, T> {

    /**
     * Converts {@code source} into the target type.
     *
     * @param source the value to convert; may be {@code null}
     * @param ctx    the current mapping context; never {@code null}
     * @return the converted value; may be {@code null}
     */
    T convert(S source, MappingContext ctx);

    /**
     * Convenience overload that passes an empty context.
     */
    default T convert(S source) {
        return convert(source, MappingContext.empty());
    }
}
