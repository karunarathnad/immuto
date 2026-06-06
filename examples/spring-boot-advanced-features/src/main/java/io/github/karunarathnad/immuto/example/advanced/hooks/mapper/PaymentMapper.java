package io.github.karunarathnad.immuto.example.advanced.hooks.mapper;

import io.github.karunarathnad.immuto.annotation.AfterMapping;
import io.github.karunarathnad.immuto.annotation.BeforeMapping;
import io.github.karunarathnad.immuto.annotation.RecordMapper;
import io.github.karunarathnad.immuto.example.advanced.hooks.model.PaymentDTO;
import io.github.karunarathnad.immuto.example.advanced.hooks.model.PaymentEntity;

import java.util.Objects;
import java.util.Set;

// @BeforeMapping and @AfterMapping are lifecycle hooks on the mapper interface.
// The processor generates calls to these default methods around the mapping logic.
// MapStruct supports the same concept but it is clumsy with records because records
// have no setters — hook methods can only observe or throw, never mutate the target.
// Immuto makes this explicit: @AfterMapping receives an already-constructed, immutable target.
@RecordMapper(componentModel = "spring")
public interface PaymentMapper {

    PaymentDTO toDto(PaymentEntity source);

    // Called before mapping — good for input validation and precondition checks.
    @BeforeMapping
    default void validate(PaymentEntity source) {
        Objects.requireNonNull(source.id(),        "payment id must not be null");
        Objects.requireNonNull(source.currency(),  "currency must not be null");
        Objects.requireNonNull(source.amount(),    "amount must not be null");
        if (!Set.of("USD", "EUR", "GBP").contains(source.currency())) {
            throw new IllegalArgumentException("unsupported currency: " + source.currency());
        }
    }

    // Called after mapping — good for audit logging, metrics, or side effects.
    // The target record is already constructed and immutable; this hook cannot change it.
    @AfterMapping
    default void audit(PaymentEntity source, PaymentDTO target) {
        System.out.printf("[AUDIT] payment %d mapped → %s %s (ref: %s)%n",
            target.id(), target.amount(), target.currency(), target.reference());
    }
}
