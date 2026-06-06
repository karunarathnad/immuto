package io.github.karunarathnad.immuto.example.advanced.collections.mapper;

import io.github.karunarathnad.immuto.annotation.RecordMapper;
import io.github.karunarathnad.immuto.example.advanced.collections.model.CatalogDTO;
import io.github.karunarathnad.immuto.example.advanced.collections.model.CatalogEntity;

/**
 * Demonstrates automatic {@code Set<Record>} and {@code Map<K, Record>} collection mapping.
 *
 * <p>MapStruct requires hand-written collection mapping methods for Set and Map values.
 * Immuto auto-maps {@code Set<Record>} (collected into an unmodifiable Set) and
 * {@code Map<K, Record>} (same key type, values mapped element-by-element) with no
 * extra configuration.
 */
@RecordMapper(componentModel = "spring")
public interface CatalogMapper {

    CatalogDTO toDto(CatalogEntity source);
}
