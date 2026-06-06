package io.github.karunarathnad.immuto.example.advanced.dotnotation.model;

// Flattened view: the nested WarehouseEntity is projected into two top-level fields.
public record ShipmentDTO(Long id, String trackingCode, String originCity, String originCountry) {}
