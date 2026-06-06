package io.github.karunarathnad.immuto.example.advanced.inverse.model;

// 'description' is exposed as 'summary' in the API layer.
public record ProductDTO(Long id, String sku, String summary, String category) {}
