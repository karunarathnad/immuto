package io.github.karunarathnad.immuto.example.advanced.dotnotation.model;

public record ShipmentEntity(Long id, String trackingCode, WarehouseEntity origin) {}
