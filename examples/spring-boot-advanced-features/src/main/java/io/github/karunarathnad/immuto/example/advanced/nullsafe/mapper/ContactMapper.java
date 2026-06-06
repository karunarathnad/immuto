package io.github.karunarathnad.immuto.example.advanced.nullsafe.mapper;

import io.github.karunarathnad.immuto.annotation.NullSafe;
import io.github.karunarathnad.immuto.annotation.RecordMapper;
import io.github.karunarathnad.immuto.example.advanced.nullsafe.model.ContactDTO;
import io.github.karunarathnad.immuto.example.advanced.nullsafe.model.ContactEntity;

import java.util.Optional;

/**
 * Demonstrates {@code @NullSafe} Optional wrapping.
 *
 * <p>MapStruct has no {@code @NullSafe} equivalent -- you write explicit null guards by hand.
 * {@code @NullSafe} tells the processor to generate:
 * <pre>
 *   if (source == null) return Optional.empty();
 *   return Optional.of(new ContactDTO(...));
 * </pre>
 * The generated method is safe to call with a null source without any NPE risk.
 */
@RecordMapper(componentModel = "spring")
public interface ContactMapper {

    @NullSafe
    Optional<ContactDTO> toDto(ContactEntity source);
}
