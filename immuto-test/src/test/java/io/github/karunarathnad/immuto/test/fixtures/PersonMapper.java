package io.github.karunarathnad.immuto.test.fixtures;

import io.github.karunarathnad.immuto.annotation.*;

/**
 * Demonstrates expression mapping, lifecycle hooks, and nested/list auto-mapping.
 */
@RecordMapper
public interface PersonMapper {

    @Mapping(target = "fullName",
             expression = "java(source.firstName() + \" \" + source.lastName())")
    PersonDTO toDto(PersonEntity source);

    @BeforeMapping
    default void validateSource(PersonEntity source) {
        if (source.id() == null) throw new IllegalArgumentException("PersonEntity.id must not be null");
    }

    @AfterMapping
    default void postMap(PersonEntity source, PersonDTO target) {
        // extension point — e.g. audit logging
    }
}
