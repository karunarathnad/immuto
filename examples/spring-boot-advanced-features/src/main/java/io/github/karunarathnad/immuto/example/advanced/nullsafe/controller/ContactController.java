package io.github.karunarathnad.immuto.example.advanced.nullsafe.controller;

import io.github.karunarathnad.immuto.example.advanced.nullsafe.mapper.ContactMapper;
import io.github.karunarathnad.immuto.example.advanced.nullsafe.model.ContactDTO;
import io.github.karunarathnad.immuto.example.advanced.nullsafe.model.ContactEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/contacts")
public class ContactController {

    private static final Map<Long, ContactEntity> STORE = Map.of(
        1L, new ContactEntity(1L, "Alice Smith",  "+1-555-0101"),
        2L, new ContactEntity(2L, "Bob Jones",    "+1-555-0102")
    );

    private final ContactMapper mapper;

    public ContactController(ContactMapper mapper) {
        this.mapper = mapper;
    }

    // GET /contacts -- lists all contacts mapped to DTOs
    @GetMapping
    public List<ContactDTO> list() {
        return STORE.values().stream()
            .map(mapper::toDto)
            .filter(java.util.Optional::isPresent)
            .map(java.util.Optional::get)
            .toList();
    }

    // GET /contacts/{id} -- returns 404 when not found instead of 500/NPE.
    // mapper.toDto(null) returns Optional.empty() thanks to @NullSafe.
    @GetMapping("/{id}")
    public ResponseEntity<ContactDTO> get(@PathVariable Long id) {
        ContactEntity entity = STORE.get(id);
        return mapper.toDto(entity)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
