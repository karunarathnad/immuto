package io.github.karunarathnad.immuto.example.advanced.strictmapping.model;

/**
 * DTO with a 'version' component that has no matching field in ReportEntity.
 * Without @Mapping(target = "version", ignore = true) on the mapper method,
 * Immuto fails the build — it never silently maps an unresolved component to null.
 */
public record ReportDTO(Long id, String title, String status, String version) {}
