package io.github.karunarathnad.immuto.example.advanced.collections.controller;

import io.github.karunarathnad.immuto.example.advanced.collections.mapper.CatalogMapper;
import io.github.karunarathnad.immuto.example.advanced.collections.model.CategoryEntity;
import io.github.karunarathnad.immuto.example.advanced.collections.model.CatalogDTO;
import io.github.karunarathnad.immuto.example.advanced.collections.model.CatalogEntity;
import io.github.karunarathnad.immuto.example.advanced.collections.model.TagEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/catalogs")
public class CatalogController {

    private final CatalogMapper mapper;

    public CatalogController(CatalogMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping
    public List<CatalogDTO> list() {
        List<CatalogEntity> entities = List.of(
            new CatalogEntity(
                "Electronics",
                Set.of(new TagEntity("sale"), new TagEntity("new-arrivals")),
                Map.of(
                    "phones",   new CategoryEntity("phones",   "Mobile Phones"),
                    "laptops",  new CategoryEntity("laptops",  "Laptops & Notebooks"),
                    "tablets",  new CategoryEntity("tablets",  "Tablets & E-readers")
                )
            ),
            new CatalogEntity(
                "Books",
                Set.of(new TagEntity("bestseller")),
                Map.of(
                    "fiction",     new CategoryEntity("fiction",     "Fiction"),
                    "non-fiction", new CategoryEntity("non-fiction", "Non-Fiction")
                )
            )
        );
        return entities.stream().map(mapper::toDto).toList();
    }
}
