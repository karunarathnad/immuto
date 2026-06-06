package io.github.karunarathnad.immuto.example.advanced.dotnotation.mapper;

import io.github.karunarathnad.immuto.annotation.Mapping;
import io.github.karunarathnad.immuto.annotation.RecordMapper;
import io.github.karunarathnad.immuto.example.advanced.dotnotation.model.ShipmentDTO;
import io.github.karunarathnad.immuto.example.advanced.dotnotation.model.ShipmentEntity;

// Dot-notation in source paths lets you flatten a nested record into top-level DTO fields.
// The processor generates null-safe accessor chains:
//   source.origin() == null ? null : source.origin().city()
// MapStruct supports dot-notation too, but only after its setters-first mapping pass —
// Immuto applies null safety directly inside the canonical constructor call.
@RecordMapper(componentModel = "spring")
public interface ShipmentMapper {

    @Mapping(target = "originCity",    source = "origin.city")
    @Mapping(target = "originCountry", source = "origin.country")
    ShipmentDTO toDto(ShipmentEntity source);
}
