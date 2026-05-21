package io.github.karunarathnad.immuto.test.fixtures;

import io.github.karunarathnad.immuto.annotation.RecordMapper;

/** Minimal mapper used to test zero-config same-name component mapping. */
@RecordMapper
public interface SimpleProductMapper {
    ProductDTO toDto(ProductEntity entity);
    ProductEntity toEntity(ProductDTO dto);
}
