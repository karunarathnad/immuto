# Immuto - Java Records-First Object Mapper

[![Maven Central](https://img.shields.io/maven-central/v/io.github.karunarathnad/immuto-core)](https://central.sonatype.com/namespace/io.github.karunarathnad)
[![CI](https://github.com/karunarathnad/immuto/actions/workflows/ci.yml/badge.svg?branch=master)](https://github.com/karunarathnad/immuto/actions/workflows/ci.yml)
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

**Maven**

```xml
<!-- Annotations: only needed at compile time; you can mark it optional -->
<dependency>
    <groupId>io.github.karunarathnad</groupId>
    <artifactId>immuto-annotations</artifactId>
    <version>1.1.0</version>
</dependency>

<!-- Core: the only jar on your runtime classpath -->
<dependency>
    <groupId>io.github.karunarathnad</groupId>
    <artifactId>immuto-core</artifactId>
    <version>1.1.0</version>
</dependency>

<!-- Processor: runs during javac, never on the runtime classpath -->
<dependency>
    <groupId>io.github.karunarathnad</groupId>
    <artifactId>immuto-processor</artifactId>
    <version>1.1.0</version>
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
                <version>1.1.0</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

**Gradle**

```groovy
dependencies {
    implementation   'io.github.karunarathnad:immuto-core:1.1.0'
    compileOnly      'io.github.karunarathnad:immuto-annotations:1.1.0'
    annotationProcessor 'io.github.karunarathnad:immuto-processor:1.1.0'
}
```

Or with the Kotlin DSL:

```kotlin
dependencies {
    implementation("io.github.karunarathnad:immuto-core:1.1.0")
    compileOnly("io.github.karunarathnad:immuto-annotations:1.1.0")
    annotationProcessor("io.github.karunarathnad:immuto-processor:1.1.0")
}
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

        PersonDTO __result = new PersonDTO(    // ← canonical constructor, never a setter
            source.id(),                       // -> id
            source.firstName() + " " + source.lastName(),  // -> fullName  (expression)
            source.email(),                    // -> email
            source.address() == null ? null    // -> address  (null-guarded nested record)
                : new com.example.AddressDTO(
                    source.address().street(),
                    source.address().city(),
                    source.address().postalCode(),
                    source.address().country()
                )
        );

        return __result;
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

Here is what those errors look like - clear, actionable messages pointing at the exact mapper method:

**Unmapped component:**
```
error: [Immuto] Target component 'fullName' has no matching source component.
       Use @Mapping(target="fullName", ignore=true) to suppress this error.
       PersonDTO toDto(PersonEntity source);
```

**Type mismatch without a converter:**
```
error: [Immuto] Cannot auto-convert component 'createdAt': no TypeConverter registered for
       java.time.Instant → java.lang.String. Register a TypeConverter or add @Mapping(expression=...).
       PersonDTO toDto(PersonEntity source);
```

**`@RecordMapper` on a class:**
```
error: [Immuto] @RecordMapper must annotate an interface, found: CLASS
       public class PersonMapper { ... }
```

Compare this to MapStruct's equivalent errors, which often reference internal code-generation internals.
Each Immuto error names the offending component and tells you exactly how to fix it.

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
// generates: return Optional.of(new AddressDTO(...))
```

### Custom type converters

> **Note:** `uses` and `qualifiedBy` in the annotation-processor path are not yet fully implemented.
> Declaring them produces a compile-time warning and the attribute is currently ignored.
> Use `FluentMapper.override(...)` for custom conversions when using the runtime API (see below),
> or replace the component with an `@Mapping(expression = "java(...)")` in the APT path.

```java
@Named("isoDate")
public class IsoDateConverter implements TypeConverter<LocalDate, String> {
    @Override
    public String convert(LocalDate source, MappingContext ctx) {
        return source == null ? null : source.toString();
    }
}

// FluentMapper (runtime API) — TypeConverter support works today:
FluentMapper<EventEntity, EventDTO> mapper = FluentMapper
    .from(EventEntity.class)
    .to(EventDTO.class)
    .override("date", e -> e.date() == null ? null : e.date().toString())
    .build();
```

### Spring / CDI integration

Add `componentModel = "spring"` to have the processor annotate the generated implementation
with `@Component`, making it injectable via `@Autowired` or constructor injection:

```java
@RecordMapper(componentModel = "spring")
public interface PersonMapper {
    PersonDTO toDto(PersonEntity source);
}
```

Generated output (excerpt):

```java
@Component
@Generated("io.github.karunarathnad.immuto.processor.RecordMapperProcessor")
public final class PersonMapperImpl implements PersonMapper, ImmutoMapper {
    ...
}
```

Then inject it like any other Spring bean:

```java
@Service
public class PersonService {
    private final PersonMapper mapper;

    public PersonService(PersonMapper mapper) {   // constructor injection
        this.mapper = mapper;
    }
}
```

> Note: add `spring-context` to your classpath so `@Component` resolves at compile time.
> Immuto itself has no Spring dependency — the annotation is generated as a plain string.

### `Collection<Record>` mapping

Beyond `List`, Immuto 1.1.0 also generates mapping code for `Set<Record>` and `Map<K, Record>`:

```java
public record DeptEntity(Set<EmployeeEntity> members) {}
public record DeptDTO   (Set<EmployeeDTO>    members) {}

public record IndexEntity(Map<String, ProjectEntity> projects) {}
public record IndexDTO   (Map<String, ProjectDTO>    projects) {}
```

Both are handled automatically by name-matching — no `@Mapping` annotation required.
The generated code uses `Collectors.toUnmodifiableSet()` and `Collectors.toUnmodifiableMap()`
so the results are always unmodifiable.

### MappingContext

`MappingContext` threads arbitrary key/value data through a mapping call tree — useful for
tenant IDs, locale, audit metadata, or any cross-cutting concern:

```java
MappingContext ctx = MappingContext.of("tenantId", "acme");

// Untyped — caller must cast
Optional<String> tenant = ctx.get("tenantId");

// Typed — preferred; throws ClassCastException with a clear message on type mismatch
Optional<String> tenant = ctx.get("tenantId", String.class);
```

Pass it into custom `TypeConverter` implementations:

```java
public class TenantAwareConverter implements TypeConverter<PriceEntity, PriceDTO> {
    @Override
    public PriceDTO convert(PriceEntity source, MappingContext ctx) {
        String tenant = ctx.get("tenantId", String.class).orElseThrow();
        return new PriceDTO(source.amount(), tenant);
    }
}
```

`MappingContext.empty()` returns a shared immutable singleton — calling `put()` on it throws
`UnsupportedOperationException`. Construct a mutable context with `MappingContext.of(...)`.

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

> **Constraints:** `FluentMapper` matches components by name. Same-named components with compatible types
> (including primitive↔wrapper pairs such as `long`↔`Long`) are mapped automatically. For other type
> conversions use `override(...)` or a `TypeConverter`, or prefer the APT path.
>
> Note: `FluentMapper` *does* use `java.lang.reflect` to read record components at runtime -
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

| Feature | MapStruct                                     | Immuto |
|---|-----------------------------------------------|---|
| Target paradigm | JavaBeans (setters)                           | Java Records (canonical constructor only) |
| Records support | Partial - requires mutable builder workaround | First-class |
| Validation timing | Compile time                                  | Compile time |
| Runtime reflection | None (generated code)                         | None (generated code) |
| Sealed classes | No                                            | Yes |
| `Optional` components | Manual                                        | `@NullSafe` |
| `Set<Record>` / `Map<K,Record>` | Manual                                        | Auto-mapped |
| Spring integration | `componentModel = "spring"`                   | `componentModel = "spring"` |
| Fluent runtime API | No                                            | Yes (`FluentMapper`) |

---

## Examples

| Example | Description |
|---|---|
| [Spring Boot Quickstart](examples/spring-boot-quickstart) | Spring Boot 3 app with constructor injection, expression mapping, and a REST controller |
| [Spring Boot Advanced Features](examples/spring-boot-advanced-features) | Six self-contained feature demos targeting the cases MapStruct cannot match (see below) |

### Advanced Features example — what each package shows

| Package | Endpoint | Feature |
|---|---|---|
| `nested/` | `GET /orders` | Nested record + `List<Record>` auto-mapped — no `uses = {OtherMapper.class}` declarations needed |
| `collections/` | `GET /catalogs` | `Set<Record>` and `Map<K,Record>` auto-collected into unmodifiable Set/Map |
| `nullsafe/` | `GET /contacts/{id}` | `@NullSafe` returns `Optional.empty()` on a null source instead of NPE |
| `hooks/` | `POST /payments` | `@BeforeMapping` validates input; `@AfterMapping` logs audit — both inlined around the constructor call |
| `inverse/` | `POST /products` | `@InheritInverseConfiguration` derives the reverse rename without repeating the declaration |
| `dotnotation/` | `GET /shipments` | `source = "origin.city"` flattens a nested record field with a generated null-safe accessor chain |

---

## Source & Issues

GitHub: <https://github.com/karunarathnad/immuto>

---

## License

Apache License 2.0 - see [LICENSE](LICENSE).