package io.github.karunarathnad.immuto.example.advanced.hooks.mapper;

import io.github.karunarathnad.immuto.annotation.AfterMapping;
import io.github.karunarathnad.immuto.annotation.BeforeMapping;
import io.github.karunarathnad.immuto.annotation.RecordMapper;
import io.github.karunarathnad.immuto.example.advanced.hooks.model.PaymentDTO;
import io.github.karunarathnad.immuto.example.advanced.hooks.model.PaymentEntity;

import java.util.Set;

/**
 * Demonstrates {@code @BeforeMapping} and {@code @AfterMapping} lifecycle hooks.
 *
 * <p>The processor generates calls to these default methods around the mapping logic.
 * MapStruct supports the same concept but it is clumsy with records because records
 * have no setters -- hook methods can only observe or throw, never mutate the target.
 * Immuto makes this explicit: {@code @AfterMapping} receives an already-constructed,
 * immutable target.
 */
@RecordMapper(componentModel = "spring")
public interface PaymentMapper {

    PaymentDTO toDto(PaymentEntity source);

    /** Validates required fields and rejects unsupported currencies before mapping proceeds. */
    @BeforeMapping
    default void validate(PaymentEntity source) {
        if (source.id()       == null) throw new IllegalArgumentException("payment id must not be null");
        if (source.currency() == null) throw new IllegalArgumentException("currency must not be null");
        if (source.amount()   == null) throw new IllegalArgumentException("amount must not be null");
        if (!Set.of("USD", "EUR", "GBP").contains(source.currency())) {
            throw new IllegalArgumentException("unsupported currency: " + source.currency());
        }
    }

    /** Logs a structured audit line after the target record has been constructed. */
    @AfterMapping
    default void audit(PaymentEntity source, PaymentDTO target) {
        System.out.printf("[AUDIT] payment %d mapped -> %s %s (ref: %s)%n",
            target.id(), target.amount(), target.currency(), target.reference());
    }
}
