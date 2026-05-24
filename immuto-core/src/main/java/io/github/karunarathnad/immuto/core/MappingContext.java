package io.github.karunarathnad.immuto.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Carries arbitrary key/value data through a mapping call tree.
 *
 * <p>A {@code MappingContext} instance is created once per top-level mapper
 * call and threaded through nested converters. It is mutable but not
 * thread-safe — do not share a single instance across concurrent mapping calls.
 *
 * <pre>{@code
 * MappingContext ctx = MappingContext.of("tenantId", "acme");
 * OrderDTO dto = mapper.toDto(entity, ctx);
 * }</pre>
 */
public final class MappingContext {

    private static final MappingContext EMPTY = new MappingContext(Collections.emptyMap());

    private final Map<String, Object> attributes;

    private MappingContext(Map<String, Object> attributes) {
        this.attributes = new HashMap<>(attributes);
    }

    /** Returns a shared, immutable empty context. */
    public static MappingContext empty() {
        return EMPTY;
    }

    /** Creates a context pre-populated with a single entry. */
    public static MappingContext of(String key, Object value) {
        return new MappingContext(Map.of(key, value));
    }

    /** Creates a context pre-populated from an existing map. */
    public static MappingContext of(Map<String, Object> attributes) {
        return new MappingContext(attributes);
    }

    /**
     * Returns the value for {@code key}, cast to {@code T}.
     *
     * @throws ClassCastException if the value is not assignable to {@code T}
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String key) {
        return Optional.ofNullable((T) attributes.get(key));
    }

    /**
     * Returns the value for {@code key} cast to {@code type}, or empty if absent.
     *
     * <p>Prefer this overload over {@link #get(String)} when the target type is known
     * at the call site — it produces a clearer {@link ClassCastException} message on
     * type mismatches and removes the need for an unchecked cast on the caller side.
     *
     * @throws ClassCastException if the stored value is not an instance of {@code type}
     */
    public <T> Optional<T> get(String key, Class<T> type) {
        Object value = attributes.get(key);
        if (value == null) return Optional.empty();
        return Optional.of(type.cast(value));
    }

    /** Stores {@code value} under {@code key}. Returns {@code this} for chaining. */
    public MappingContext put(String key, Object value) {
        if (this == EMPTY) {
            throw new UnsupportedOperationException("Cannot mutate the shared empty MappingContext");
        }
        attributes.put(key, value);
        return this;
    }

    /** Returns {@code true} if the key is present. */
    public boolean contains(String key) {
        return attributes.containsKey(key);
    }

    /** Returns an unmodifiable view of all attributes. */
    public Map<String, Object> attributes() {
        return Collections.unmodifiableMap(attributes);
    }
}
