package io.github.karunarathnad.immuto.example.advanced.collections.mapper;

import io.github.karunarathnad.immuto.annotation.RecordMapper;
import io.github.karunarathnad.immuto.example.advanced.collections.model.CatalogDTO;
import io.github.karunarathnad.immuto.example.advanced.collections.model.CatalogEntity;

// MapStruct requires hand-written collection mapping methods for Set and Map values.
// Immuto auto-maps Set<Record> (collected into an unmodifiable Set) and
// Map<K, Record> (same key type, values mapped element-by-element) with no extra config.
@RecordMapper(componentModel = "spring")
public interface CatalogMapper {

    CatalogDTO toDto(CatalogEntity source);
}
