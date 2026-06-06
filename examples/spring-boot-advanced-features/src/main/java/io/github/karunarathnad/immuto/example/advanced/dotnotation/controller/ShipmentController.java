package io.github.karunarathnad.immuto.example.advanced.dotnotation.controller;

import io.github.karunarathnad.immuto.example.advanced.dotnotation.mapper.ShipmentMapper;
import io.github.karunarathnad.immuto.example.advanced.dotnotation.model.ShipmentDTO;
import io.github.karunarathnad.immuto.example.advanced.dotnotation.model.ShipmentEntity;
import io.github.karunarathnad.immuto.example.advanced.dotnotation.model.WarehouseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/shipments")
public class ShipmentController {

    private final ShipmentMapper mapper;

    public ShipmentController(ShipmentMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping
    public List<ShipmentDTO> list() {
        List<ShipmentEntity> entities = List.of(
            new ShipmentEntity(1L, "TRACK-AA001", new WarehouseEntity("WH-NY",  "New York",  "US")),
            new ShipmentEntity(2L, "TRACK-BB002", new WarehouseEntity("WH-LHR", "London",    "GB")),
            // null origin -- dot-notation generates a null guard so this does not NPE
            new ShipmentEntity(3L, "TRACK-CC003", null)
        );
        return entities.stream().map(mapper::toDto).toList();
    }
}
