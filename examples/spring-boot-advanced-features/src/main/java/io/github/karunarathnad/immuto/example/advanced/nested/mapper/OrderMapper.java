package io.github.karunarathnad.immuto.example.advanced.nested.mapper;

import io.github.karunarathnad.immuto.annotation.RecordMapper;
import io.github.karunarathnad.immuto.example.advanced.nested.model.OrderDTO;
import io.github.karunarathnad.immuto.example.advanced.nested.model.OrderEntity;

// MapStruct requires separate @Mapper interfaces for each nested type and wires them
// together via @Mapper(uses = {CustomerMapper.class, LineItemMapper.class}).
// Immuto resolves nested record mappings and List<Record> element mappings automatically —
// a single interface method is all that is needed.
@RecordMapper(componentModel = "spring")
public interface OrderMapper {

    OrderDTO toDto(OrderEntity source);
}
