package io.github.karunarathnad.immuto.example.advanced.nested.model;

import java.util.List;

public record OrderDTO(Long id, String reference, CustomerDTO customer, List<LineItemDTO> items) {}
