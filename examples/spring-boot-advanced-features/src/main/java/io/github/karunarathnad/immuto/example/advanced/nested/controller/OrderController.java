package io.github.karunarathnad.immuto.example.advanced.nested.controller;

import io.github.karunarathnad.immuto.example.advanced.nested.mapper.OrderMapper;
import io.github.karunarathnad.immuto.example.advanced.nested.model.CustomerEntity;
import io.github.karunarathnad.immuto.example.advanced.nested.model.LineItemEntity;
import io.github.karunarathnad.immuto.example.advanced.nested.model.OrderDTO;
import io.github.karunarathnad.immuto.example.advanced.nested.model.OrderEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderMapper mapper;

    public OrderController(OrderMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping
    public List<OrderDTO> list() {
        List<OrderEntity> entities = List.of(
            new OrderEntity(
                1L, "ORD-001",
                new CustomerEntity(10L, "Alice Smith", "alice@example.com"),
                List.of(
                    new LineItemEntity("SKU-A", "Widget Pro", 2),
                    new LineItemEntity("SKU-B", "Gadget Plus", 1)
                )
            ),
            new OrderEntity(
                2L, "ORD-002",
                new CustomerEntity(11L, "Bob Jones", "bob@example.com"),
                List.of(
                    new LineItemEntity("SKU-C", "Super Gizmo", 5)
                )
            )
        );
        return entities.stream().map(mapper::toDto).toList();
    }
}
