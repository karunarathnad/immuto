package io.github.karunarathnad.immuto.test.fixtures;

import java.time.LocalDate;
import java.util.List;

/**
 * API-layer DTO record — components differ from {@link PersonEntity}
 * to exercise the mapper's renaming and expression capabilities.
 */
public record PersonDTO(
        Long id,
        String fullName,
        String email,
        LocalDate birthDate,
        AddressDTO address,
        List<PhoneDTO> phones
) {}
