package io.github.karunarathnad.immuto.core;

import java.util.*;
import java.util.function.Function;

/**
 * Fluent, programmatic mapper for cases where compile-time annotation
 * processing is not available (e.g., dynamic environments, tests).
 *
 * <p>Component resolution follows three steps in order:
 * <ol>
 *   <li>Explicit overrides registered via {@link #override}</li>
 *   <li>Same-name component lookup via {@link RecordIntrospector}</li>
 *   <li>Registered {@link TypeConverter} for the source→target type pair</li>
 * </ol>
 *
 * <pre>{@code
 * FluentMapper<PersonEntity, PersonDTO> mapper = FluentMapper
 *     .from(PersonEntity.class)
 *     .to(PersonDTO.class)
 *     .override("fullName", e -> e.firstName() + " " + e.lastName())
 *     .build();
 *
 * PersonDTO dto = mapper.map(entity);
 * }</pre>
 *
 * @param <S> source type (must be a Java record)
 * @param <T> target type (must be a Java record)
 */
public final class FluentMapper<S, T> {

    private record RegisteredConverter(Class<?> sourceType, TypeConverter<?, ?> converter) {
        boolean canHandle(Object value) {
            return sourceType == null || value == null || sourceType.isInstance(value);
        }
    }

    private final Class<S> sourceClass;
    private final Class<T> targetClass;
    private final Map<String, Function<S, Object>> overrides;
    private final Map<String, String> renames;
    private final Set<String> ignored;
    private final List<RegisteredConverter> converters;

    private FluentMapper(Builder<S, T> builder) {
        this.sourceClass = builder.sourceClass;
        this.targetClass = builder.targetClass;
        this.overrides   = Map.copyOf(builder.overrides);
        this.renames     = Map.copyOf(builder.renames);
        this.ignored     = Set.copyOf(builder.ignored);
        this.converters  = List.copyOf(builder.converters);
    }

    /** Maps {@code source} using an empty {@link MappingContext}. */
    public T map(S source) {
        return map(source, MappingContext.empty());
    }

    /**
     * Maps {@code source} to a new target record instance.
     *
     * @param source the source record; if {@code null}, returns {@code null}
     * @param ctx    mapping context threaded through converters
     */
    public T map(S source, MappingContext ctx) {
        if (source == null) return null;

        List<ImmutoRecordComponent> targetComponents = RecordIntrospector.components(targetClass);
        Map<String, ImmutoRecordComponent> sourceMap = RecordIntrospector.componentMap(sourceClass);

        Object[] args = new Object[targetComponents.size()];
        for (ImmutoRecordComponent target : targetComponents) {
            String name = target.name();

            if (ignored.contains(name)) {
                args[target.index()] = null;
                continue;
            }

            if (overrides.containsKey(name)) {
                args[target.index()] = overrides.get(name).apply(source);
                continue;
            }

            String sourceName = renames.getOrDefault(name, name);
            ImmutoRecordComponent sourceComp = sourceMap.get(sourceName);
            if (sourceComp != null) {
                Object raw = sourceComp.read(source);
                args[target.index()] = convert(raw, sourceComp.type(), target.type(), ctx);
            }
            // else: leave null — the record's canonical constructor will receive null
        }
        return RecordIntrospector.instantiate(targetClass, args);
    }

    /** Maps every element of {@code sources}, preserving order. */
    public List<T> mapAll(List<S> sources) {
        return mapAll(sources, MappingContext.empty());
    }

    public List<T> mapAll(List<S> sources, MappingContext ctx) {
        if (sources == null) return null;
        List<T> result = new ArrayList<>(sources.size());
        for (S s : sources) result.add(map(s, ctx));
        return Collections.unmodifiableList(result);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object convert(Object value, Class<?> sourceType, Class<?> targetType, MappingContext ctx) {
        for (RegisteredConverter rc : converters) {
            if (!rc.canHandle(value)) continue;
            TypeConverter converter = rc.converter();
            try {
                return converter.convert(value, ctx);
            } catch (ClassCastException e) {
                if (rc.sourceType() != null) throw e; // internal bug in typed converter — don't swallow
                // untyped converter: CCE indicates type mismatch at dispatch, try next
            }
        }

        if (value == null) return null;
        if (targetType.isAssignableFrom(sourceType)) return value;

        if (targetType.isRecord() && sourceType.isRecord()) {
            return RecordIntrospector.shallowCopy(value, targetType);
        }

        throw new MappingException(String.format(
                "No converter found for %s → %s", sourceType.getName(), targetType.getName()));
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static <S> SourceStep<S> from(Class<S> sourceClass) {
        return new SourceStep<>(sourceClass);
    }

    public static final class SourceStep<S> {
        private final Class<S> sourceClass;
        private SourceStep(Class<S> sourceClass) { this.sourceClass = sourceClass; }

        public <T> Builder<S, T> to(Class<T> targetClass) {
            return new Builder<>(sourceClass, targetClass);
        }
    }

    public static final class Builder<S, T> {
        private final Class<S> sourceClass;
        private final Class<T> targetClass;
        private final Map<String, Function<S, Object>> overrides = new LinkedHashMap<>();
        private final Map<String, String> renames = new LinkedHashMap<>();
        private final Set<String> ignored = new LinkedHashSet<>();
        private final List<RegisteredConverter> converters = new ArrayList<>();

        private Builder(Class<S> sourceClass, Class<T> targetClass) {
            this.sourceClass = sourceClass;
            this.targetClass = targetClass;
        }

        /**
         * Overrides the mapping for {@code targetComponent} with a custom function.
         *
         * @param targetComponent the target record component name
         * @param extractor       function that extracts/computes the value from the source
         */
        public Builder<S, T> override(String targetComponent, Function<S, Object> extractor) {
            overrides.put(targetComponent, extractor);
            return this;
        }

        /**
         * Maps source component {@code sourceName} to target component {@code targetName}.
         */
        public Builder<S, T> rename(String sourceName, String targetName) {
            renames.put(targetName, sourceName);
            return this;
        }

        /**
         * Excludes the target component from mapping (it will receive {@code null}).
         */
        public Builder<S, T> ignore(String targetComponent) {
            ignored.add(targetComponent);
            return this;
        }

        /** Registers a {@link TypeConverter} for use during this mapping. */
        public Builder<S, T> converter(TypeConverter<?, ?> converter) {
            converters.add(new RegisteredConverter(null, converter));
            return this;
        }

        /**
         * Registers a type-safe {@link TypeConverter} that only handles values
         * of {@code sourceType}. Unlike the untyped overload, a {@link ClassCastException}
         * thrown inside this converter is never swallowed — it propagates immediately.
         */
        public <C> Builder<S, T> converter(Class<C> sourceType, TypeConverter<C, ?> converter) {
            converters.add(new RegisteredConverter(sourceType, converter));
            return this;
        }

        public FluentMapper<S, T> build() {
            return new FluentMapper<>(this);
        }
    }
}
