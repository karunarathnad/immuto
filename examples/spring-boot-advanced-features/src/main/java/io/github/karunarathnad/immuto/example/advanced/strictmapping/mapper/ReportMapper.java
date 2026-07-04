package io.github.karunarathnad.immuto.example.advanced.strictmapping.mapper;

import io.github.karunarathnad.immuto.annotation.Mapping;
import io.github.karunarathnad.immuto.annotation.RecordMapper;
import io.github.karunarathnad.immuto.example.advanced.strictmapping.model.ReportDTO;
import io.github.karunarathnad.immuto.example.advanced.strictmapping.model.ReportEntity;

/**
 * Demonstrates Immuto's strict-by-default unmapped component policy.
 *
 * <p>ReportDTO has a 'version' component that does not exist in ReportEntity.
 * Removing the @Mapping(target = "version", ignore = true) line below causes
 * the build to fail with:
 *
 * <pre>
 *   error: [Immuto] Target component 'version' has no matching source component.
 *          Use @Mapping(target="version", ignore=true) to suppress this error.
 *          ReportDTO toDto(ReportEntity source);
 * </pre>
 *
 * <p>MapStruct's default policy is IGNORE — it silently maps 'version' to null
 * and the build succeeds. Immuto forces an explicit decision at compile time.
 *
 * <p>To opt into lenient mode project-wide, set:
 * {@code @RecordMapper(warnOnUnmappedTargetComponents = true)}
 */
@RecordMapper(componentModel = "spring")
public interface ReportMapper {

    @Mapping(target = "version", ignore = true)
    ReportDTO toDto(ReportEntity source);
}
