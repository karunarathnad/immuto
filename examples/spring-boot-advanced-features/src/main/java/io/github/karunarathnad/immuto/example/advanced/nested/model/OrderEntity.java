package io.github.karunarathnad.immuto.example.advanced.nested.model;

import java.util.List;

public record OrderEntity(Long id, String reference, CustomerEntity customer, List<LineItemEntity> items) {}
