package io.github.karunarathnad.immuto.test.fixtures;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Persistence-layer record used by mapper integration tests.
 */
public record PersonEntity(
        Long id,
        String firstName,
        String lastName,
        String email,
        LocalDate birthDate,
        AddressEntity address,
        List<PhoneEntity> phones,
        String internalNote
) {}
