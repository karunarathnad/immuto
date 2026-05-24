package io.github.karunarathnad.immuto.example.controller;

import io.github.karunarathnad.immuto.example.mapper.PersonMapper;
import io.github.karunarathnad.immuto.example.model.PersonDTO;
import io.github.karunarathnad.immuto.example.model.PersonEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/persons")
public class PersonController {

    private final PersonMapper mapper;

    public PersonController(PersonMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping
    public List<PersonDTO> list() {
        // In a real app these would come from a repository
        List<PersonEntity> entities = List.of(
                new PersonEntity(1L, "Jane", "Doe", "jane@example.com"),
                new PersonEntity(2L, "John", "Smith", "john@example.com")
        );
        return entities.stream().map(mapper::toDto).toList();
    }

    @PostMapping
    public PersonEntity create(@RequestBody PersonDTO dto) {
        return mapper.toEntity(dto);
    }
}
