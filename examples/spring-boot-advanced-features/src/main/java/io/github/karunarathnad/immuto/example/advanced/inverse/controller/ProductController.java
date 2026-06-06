package io.github.karunarathnad.immuto.example.advanced.inverse.controller;

import io.github.karunarathnad.immuto.example.advanced.inverse.mapper.ProductMapper;
import io.github.karunarathnad.immuto.example.advanced.inverse.model.ProductDTO;
import io.github.karunarathnad.immuto.example.advanced.inverse.model.ProductEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductMapper mapper;

    public ProductController(ProductMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping
    public List<ProductDTO> list() {
        List<ProductEntity> entities = List.of(
            new ProductEntity(1L, "WGT-001", "Heavy-duty industrial widget", "hardware"),
            new ProductEntity(2L, "GDG-042", "Compact wireless gadget",       "electronics")
        );
        return entities.stream().map(mapper::toDto).toList();
    }

    // POST /products -- accepts a DTO and maps back to entity using the auto-derived inverse.
    @PostMapping
    public ProductEntity create(@RequestBody ProductDTO dto) {
        return mapper.toEntity(dto);
    }
}
