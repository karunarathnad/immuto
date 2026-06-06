package io.github.karunarathnad.immuto.example.advanced.nullsafe.mapper;

import io.github.karunarathnad.immuto.annotation.NullSafe;
import io.github.karunarathnad.immuto.annotation.RecordMapper;
import io.github.karunarathnad.immuto.example.advanced.nullsafe.model.ContactDTO;
import io.github.karunarathnad.immuto.example.advanced.nullsafe.model.ContactEntity;

import java.util.Optional;

// MapStruct has no @NullSafe equivalent — you write explicit null guards by hand.
// @NullSafe tells the processor to generate:
//   if (source == null) return Optional.empty();
//   return Optional.of(new ContactDTO(...));
// The generated method is safe to call with a null source without any NPE risk.
@RecordMapper(componentModel = "spring")
public interface ContactMapper {

    @NullSafe
    Optional<ContactDTO> toDto(ContactEntity source);
}
