package io.github.karunarathnad.immuto.core;

/**
 * Thrown when a runtime mapping operation fails — for example, when the
 * fluent API cannot locate a matching record component or a
 * {@link TypeConverter} raises an unexpected error.
 *
 * <p>Processor-generated mappers perform all validation at compile time
 * and will never throw this exception for structural issues; it is reserved
 * for runtime-only paths such as the reflective fallback in
 * {@link RecordIntrospector}.
 */
public class MappingException extends RuntimeException {

    public MappingException(String message) {
        super(message);
    }

    public MappingException(String message, Throwable cause) {
        super(message, cause);
    }
}
