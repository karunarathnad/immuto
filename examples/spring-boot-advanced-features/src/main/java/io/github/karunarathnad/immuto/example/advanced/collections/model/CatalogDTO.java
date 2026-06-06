package io.github.karunarathnad.immuto.example.advanced.collections.model;

import java.util.Map;
import java.util.Set;

public record CatalogDTO(String name, Set<TagDTO> tags, Map<String, CategoryDTO> categories) {}
