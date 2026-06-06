package io.github.karunarathnad.immuto.example.advanced.collections.model;

import java.util.Map;
import java.util.Set;

public record CatalogEntity(String name, Set<TagEntity> tags, Map<String, CategoryEntity> categories) {}
