# Immuto - Java Records-First Object Mapper

[![Maven Central](https://img.shields.io/maven-central/v/io.github.karunarathnad/immuto-core)](https://central.sonatype.com/namespace/io.github.karunarathnad)
[![Java 21+](https://img.shields.io/badge/Java-21%2B-blue.svg)](https://openjdk.org/projects/jdk/21/)
[![License](https://img.shields.io/badge/License-Apache_2.0-green.svg)](LICENSE)

Immuto is the first object mapper designed *specifically* for Java Records.
Unlike existing libraries (MapStruct, ModelMapper, Orika, JMapper) that treat Records as an afterthought,
Immuto uses **canonical constructors** as the sole mapping target - no setters, no surprises.

> **How it works in one sentence:** Immuto is an [annotation processor](https://openjdk.org/groups/compiler/doc/compilation-overview/index.html) - 
> `javac` runs it during your normal `mvn compile` step and writes plain `.java` source files to
> `target/generated-sources`. Those files call your record's canonical constructor directly.
> **Nothing happens at runtime** beyond loading that generated class.
> This is the same compile-time approach used by MapStruct and Lombok - not runtime reflection like
> ModelMapper or Spring's `BeanUtils`.

---

## Why Immuto?

| Library | Records support | When code runs | Null safety | Sealed classes |
|---|---|---|---|---|
| MapStruct | Partial (bolted-on) | Compile-time APT → **setters first** | Manual | No |
| ModelMapper | Broken (reflection field-set) | **Runtime reflection** | No | No |
| Orika | Broken (bytecode setters) | **Runtime bytecode gen** | No | No |
| JMapper | Partial | **Runtime byte manipulation** | No | No |
| **Immuto** | **First-class** | **Compile-time APT → canonical constructor** | **`@NullSafe`** | **Yes** |

The key distinction from MapStruct: MapStruct generates setter calls and adapts to records only as a
secondary concern. Immuto treats the canonical constructor as the *only* valid target - if a component
cannot be mapped at compile time, the build fails with a clear error message.

---

## Quick Start

### 1. Add dependencies

```xml
<!-- Annotations: only needed at compile time; you can mark it optional -->
<dependency>
    <groupId>io.github.karunarathnad</groupId>
    <artifactId>immuto-annotations</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- Core: the only jar on your runtime classpath -->
<dependency>
    <groupId>io.github.karunarathnad</groupId>
    <artifactId>immuto-core</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- Processor: runs during javac, never on the runtime classpath -->
<dependency>
    <groupId>io.github.karunarathnad</groupId>
    <artifactId>immuto-processor</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```

Tell the compiler plugin where to find the processor:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <path>
                <groupId>io.github.karunarathnad</groupId>
                <artifactId>immuto-processor</artifactId>
                <version>1.0.0</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

### 2. Define your Records

```java
public record PersonEntity(Long id, String firstName, String lastName,
                           String email, AddressEntity address) {}

public record PersonDTO(Long id, String fullName, String email, AddressDTO address) {}
```

### 3. Declare a mapper interface

```java
@RecordMapper
public interface PersonMapper {

    @Mapping(target = "fullName",
             expression = "java(source.firstName() + \" \" + source.lastName())")
    PersonDTO toDto(PersonEntity source);

    @InheritInverseConfiguration(name = "toDto")
    PersonEntity toEntity(PersonDTO source);
}
```

`mvn compile` runs the processor and writes `PersonMapperImpl.java` into
`target/generated-sources/annotations/`. The generated file looks like this:

```java
// Written by javac during compilation — not executed until you call it
@Generated("io.github.karunarathnad.immuto.processor.RecordMapperProcessor")
public final class PersonMapperImpl implements PersonMapper, ImmutoMapper {

    @Override
    public PersonDTO toDto(PersonEntity source) {
        if (source == null) return null;
        return new PersonDTO(           // ← canonical constructor, never a setter
            source.id(),               // -> id
            source.firstName() + " " + source.lastName(),  // -> fullName  (expression)
            source.email(),            // -> email
            new AddressDTO(            // -> address  (nested record, same components)
                source.address().street(),
                source.address().city(),
                source.address().postalCode(),
                source.address().country()
            )
        );
    }
}
```

### 4. Use it

```java
PersonMapper mapper = Immuto.getMapper(PersonMapper.class);  // loads PersonMapperImpl via Class.forName
PersonDTO dto = mapper.toDto(entity);
```

`Immuto.getMapper` does one `Class.forName` on the first call and caches the instance - no proxy, no
reflection on every map call, no byte-code generation at runtime.

---

## Features

### Compile-time validation, zero runtime surprises

The processor validates every target record component before your build finishes:

- Unmapped components → **build error** (not a silent null at runtime)
- Type mismatch with no registered converter → **build error**
- `@RecordMapper` on a class instead of an interface → **build error**

### Nested Record mapping

Record-typed components are mapped by matching component names recursively.
Use `@Mapping(expression=...)` for asymmetric or computed nesting.

### `List<Record>` → `List<Record>`

Generated code streams and maps element-by-element - no reflection involved.

### `@Mapping` overrides

```java
@Mapping(target = "fullName", expression = "java(source.firstName() + \" \" + source.lastName())")
@Mapping(target = "active",   constant   = "true")
@Mapping(target = "debug",    ignore     = true)
@Mapping(target = "city",     source     = "address.city")  // dot-notation accessor chain
```

### Bidirectional mapping

```java
@InheritInverseConfiguration(name = "toDto")
PersonEntity toEntity(PersonDTO dto);
```

### Lifecycle hooks

```java
@BeforeMapping
default void validate(PersonEntity source) {
    Objects.requireNonNull(source.id(), "id must not be null");
}

@AfterMapping
default void audit(PersonEntity source, PersonDTO target) {
    AuditLog.record(target);
}
```

Hook calls are inlined into the generated method body - no proxy, no AOP.

### Optional-safe mapping

```java
@NullSafe
Optional<AddressDTO> toAddressDto(AddressEntity entity);
// generates: return Optional.ofNullable(new AddressDTO(...))
```

### Custom type converters

```java
@Named("isoDate")
public class IsoDateConverter implements TypeConverter<LocalDate, String> {
    @Override
    public String convert(LocalDate source, MappingContext ctx) {
        return source == null ? null : source.toString();
    }
}

@RecordMapper(uses = IsoDateConverter.class)
public interface EventMapper {
    @Mapping(target = "date", qualifiedBy = "isoDate")
    EventDTO toDto(EventEntity source);
}
```

### Fluent runtime API (no annotation processor required)

For dynamic environments, tests, or cases where APT is unavailable:

```java
FluentMapper<PersonEntity, PersonDTO> mapper = FluentMapper
    .from(PersonEntity.class)
    .to(PersonDTO.class)
    .override("fullName", p -> p.firstName() + " " + p.lastName())
    .build();

PersonDTO dto        = mapper.map(entity);
List<PersonDTO> dtos = mapper.mapAll(entities);
```

> Note: `FluentMapper` *does* use `java.lang.reflect` to read record components at runtime —
> it is the explicit opt-in fallback for when you cannot use the annotation processor.
> The APT-generated path is always reflection-free.

---

## Module structure

```
io.github.karunarathnad
├── immuto-annotations   — @RecordMapper, @Mapping, @Named, … (zero dependencies)
├── immuto-core          — Immuto facade, FluentMapper, TypeConverter SPI, MappingContext
└── immuto-processor     — javac annotation processor (compile-time only, never on runtime classpath)
```

---

## Compared to MapStruct

| | MapStruct                                     | Immuto |
|---|-----------------------------------------------|---|
| Target paradigm | JavaBeans (setters)                           | Java Records (canonical constructor only) |
| Records support | Partial - requires mutable builder workaround | First-class |
| Validation timing | Compile time                                  | Compile time |
| Runtime reflection | None (generated code)                         | None (generated code) |
| Sealed classes | No                                            | Yes |
| `Optional` components | Manual                                        | `@NullSafe` |
| Fluent runtime API | No                                            | Yes (`FluentMapper`) |

---

## Source & Issues

GitHub: <https://github.com/karunarathnad/immuto>

---

## License

Apache License 2.0 - see [LICENSE](LICENSE).
