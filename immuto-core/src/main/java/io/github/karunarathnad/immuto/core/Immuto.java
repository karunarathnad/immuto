package io.github.karunarathnad.immuto.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Entry point for obtaining generated mapper instances.
 *
 * <p>The annotation processor generates a class named {@code <Interface>Impl}
 * in the same package as the mapper interface. {@code Immuto.getMapper} locates
 * and instantiates that class via a single no-arg constructor call — after the
 * first call the instance is cached.
 *
 * <pre>{@code
 * OrderMapper mapper = Immuto.getMapper(OrderMapper.class);
 * OrderDTO dto = mapper.toDto(entity);
 * }</pre>
 */
public final class Immuto {

    private static final Map<Class<?>, Object> CACHE = new ConcurrentHashMap<>();

    private Immuto() {}

    /**
     * Returns the mapper implementation for {@code mapperInterface}.
     *
     * @param mapperInterface the {@code @RecordMapper}-annotated interface
     * @param <T>             the mapper type
     * @throws MappingException if the generated implementation cannot be found or instantiated
     */
    @SuppressWarnings("unchecked")
    public static <T> T getMapper(Class<T> mapperInterface) {
        return (T) CACHE.computeIfAbsent(mapperInterface, Immuto::load);
    }

    private static <T> T load(Class<T> mapperInterface) {
        String pkg = mapperInterface.getPackageName();
        String implName = (pkg.isEmpty() ? "" : pkg + ".") + mapperInterface.getSimpleName() + "Impl";
        try {
            Class<?> implClass = Class.forName(implName, true, mapperInterface.getClassLoader());
            return mapperInterface.cast(implClass.getDeclaredConstructor().newInstance());
        } catch (ClassNotFoundException e) {
            throw new MappingException(
                    "No Immuto-generated implementation found for " + mapperInterface.getName()
                            + ". Expected class: " + implName
                            + ". Ensure immuto-processor is on the annotation processor path.", e);
        } catch (ClassCastException e) {
            throw new MappingException(
                    "Generated class " + implName + " does not implement " + mapperInterface.getName()
                            + ". Recompile the project to regenerate the mapper.", e);
        } catch (ReflectiveOperationException e) {
            throw new MappingException(
                    "Failed to instantiate " + implName, e);
        }
    }
}
