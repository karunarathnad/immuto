package io.github.karunarathnad.immuto.example.advanced.inverse.mapper;

import io.github.karunarathnad.immuto.annotation.InheritInverseConfiguration;
import io.github.karunarathnad.immuto.annotation.Mapping;
import io.github.karunarathnad.immuto.annotation.RecordMapper;
import io.github.karunarathnad.immuto.example.advanced.inverse.model.ProductDTO;
import io.github.karunarathnad.immuto.example.advanced.inverse.model.ProductEntity;

// @InheritInverseConfiguration derives the reverse mapping from the forward mapping.
// The forward declares description → summary; the inverse automatically derives
// summary → description without repeating the rename.
// MapStruct has the same annotation but it breaks on record canonical constructors
// when builders are not configured — Immuto handles it natively.
@RecordMapper(componentModel = "spring")
public interface ProductMapper {

    @Mapping(target = "summary", source = "description")
    ProductDTO toDto(ProductEntity source);

    @InheritInverseConfiguration(name = "toDto")
    ProductEntity toEntity(ProductDTO source);
}
