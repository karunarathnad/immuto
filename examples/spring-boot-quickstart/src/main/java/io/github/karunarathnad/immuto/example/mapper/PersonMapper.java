package io.github.karunarathnad.immuto.example.mapper;

import io.github.karunarathnad.immuto.annotation.Mapping;
import io.github.karunarathnad.immuto.annotation.RecordMapper;
import io.github.karunarathnad.immuto.example.model.PersonDTO;
import io.github.karunarathnad.immuto.example.model.PersonEntity;

@RecordMapper(componentModel = "spring")
public interface PersonMapper {

    @Mapping(target = "fullName", expression = "java(source.firstName() + \" \" + source.lastName())")
    PersonDTO toDto(PersonEntity source);

    @Mapping(target = "firstName", expression = "java(source.fullName().split(\" \")[0])")
    @Mapping(target = "lastName",  expression = "java(source.fullName().contains(\" \") ? source.fullName().substring(source.fullName().indexOf(\" \") + 1) : \"\")")
    PersonEntity toEntity(PersonDTO source);
}
