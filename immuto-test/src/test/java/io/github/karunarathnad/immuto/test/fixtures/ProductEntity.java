package io.github.karunarathnad.immuto.test.fixtures;

import java.math.BigDecimal;

public record ProductEntity(Long id, String name, BigDecimal price, boolean active) {}
