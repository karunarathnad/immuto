package io.github.karunarathnad.immuto.test.fixtures;

import io.github.karunarathnad.immuto.annotation.*;

/**
 * Demonstrates the full feature set of Immuto in a single mapper:
 * expression mapping, nested records, List<Record>, lifecycle hooks,
 * bidirectional mapping.
 */
@RecordMapper
public interface PersonMapper {

    @Mapping(target = "fullName",
             expression = "java(source.firstName() + \" \" + source.lastName())")
    @Mapping(target = "address")     // nested record — auto shallow-copy
    @Mapping(target = "phones")      // List<PhoneEntity> → List<PhoneDTO> auto
    PersonDTO toDto(PersonEntity source);

    @InheritInverseConfiguration(name = "toDto")
    PersonEntity toEntity(PersonDTO source);

    @BeforeMapping
    default void validateSource(PersonEntity source) {
        if (source.id() == null) throw new IllegalArgumentException("PersonEntity.id must not be null");
    }

    @AfterMapping
    default void postMap(PersonEntity source, PersonDTO target) {
        // extension point — e.g. audit logging
    }
}
