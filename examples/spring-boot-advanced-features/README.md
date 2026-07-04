# Immuto + Spring Boot - Advanced Features

A runnable Spring Boot 3 application demonstrating seven [Immuto](https://github.com/karunarathnad/immuto) features
that MapStruct either cannot handle or requires significant workarounds for.

Each feature lives in its own sub-package with its own model, mapper, and REST controller so you can
read them independently. The app runs on **port 8081** to avoid clashing with the
[quickstart](../spring-boot-quickstart) if both are running.

> **New to Immuto?** Start with the [Spring Boot Quickstart](../spring-boot-quickstart) first —
> it covers the basics (name-matched mapping, expressions, Spring injection) that this project builds on.

---

## Prerequisites

| Requirement | Version |
|---|---|
| Java | 21+ |
| Maven | 3.6.3+ |
| Spring Boot | 3.2+ |

---

## Run it

```bash
git clone https://github.com/karunarathnad/immuto.git
cd immuto/examples/spring-boot-advanced-features
mvn spring-boot:run
```

---

## Features

### 1. Strict unmapped component policy — `GET /reports`

**Package:** `strictmapping/`

MapStruct's default policy for a target field with no matching source is `IGNORE` — the field is silently mapped to `null` and the build succeeds. Immuto's default is a **build error**.

`ReportDTO` has a `version` component that does not exist in `ReportEntity`. Without `@Mapping(target = "version", ignore = true)` on the mapper, the build fails with:

```
error: [Immuto] Target component 'version' has no matching source component.
       Use @Mapping(target="version", ignore=true) to suppress this error.
       ReportDTO toDto(ReportEntity source);
```

The mapper makes the decision explicit:

```java
@RecordMapper(componentModel = "spring")
public interface ReportMapper {
    @Mapping(target = "version", ignore = true)
    ReportDTO toDto(ReportEntity source);
}
```

To reproduce the error: remove the `@Mapping` annotation and run `mvn compile`.

```bash
curl http://localhost:8081/reports
```

---

### 3. Nested record mapping — `GET /orders`

**Package:** `nested/`

MapStruct requires a separate `@Mapper` interface for each nested type and wires them together
via `@Mapper(uses = {CustomerMapper.class, LineItemMapper.class})`. Immuto resolves nested record
mappings and `List<Record>` element mappings automatically — a single interface method covers
the entire object graph.

```java
@RecordMapper(componentModel = "spring")
public interface OrderMapper {
    OrderDTO toDto(OrderEntity source);  // maps nested CustomerEntity + List<LineItemEntity> automatically
}
```

```bash
curl http://localhost:8081/orders
```

---

### 4. Collection mapping: `Set<Record>` and `Map<K, Record>` — `GET /catalogs`

**Package:** `collections/`

MapStruct requires hand-written collection mapping methods for `Set` and `Map` values.
Immuto auto-maps both — `Set<Record>` is collected into an unmodifiable `Set`, and
`Map<K, Record>` preserves the key type while mapping values element-by-element.

```java
public record CatalogEntity(String name, Set<TagEntity> tags, Map<String, CategoryEntity> categories) {}
public record CatalogDTO   (String name, Set<TagDTO>    tags, Map<String, CategoryDTO>    categories) {}

@RecordMapper(componentModel = "spring")
public interface CatalogMapper {
    CatalogDTO toDto(CatalogEntity source);  // Set and Map handled with no extra config
}
```

```bash
curl http://localhost:8081/catalogs
```

---

### 5. `@NullSafe` — `GET /contacts`, `GET /contacts/{id}`

**Package:** `nullsafe/`

MapStruct has no `@NullSafe` equivalent — you write explicit null guards by hand.
Placing `@NullSafe` on a method whose return type is `Optional<T>` tells the processor to generate:

```java
if (source == null) return Optional.empty();
return Optional.of(new ContactDTO(...));
```

The `GET /contacts/{id}` endpoint uses this to return `404` for a missing id instead of crashing.

```bash
curl http://localhost:8081/contacts        # lists all contacts
curl http://localhost:8081/contacts/1      # 200 OK
curl http://localhost:8081/contacts/99     # 404 Not Found — null source → Optional.empty()
```

---

### 6. `@BeforeMapping` and `@AfterMapping` — `POST /payments`

**Package:** `hooks/`

Lifecycle hooks are declared as `default` methods on the mapper interface.
The processor inlines calls to them around the generated constructor call — no proxy, no AOP.
MapStruct supports the same concept but it is clumsy with records because records have no setters;
Immuto makes it explicit that `@AfterMapping` receives an already-constructed, immutable target.

```java
@BeforeMapping  // called before the record constructor — throws on invalid input
default void validate(PaymentEntity source) { ... }

@AfterMapping   // called after the record is built — observe only, cannot mutate
default void audit(PaymentEntity source, PaymentDTO target) { ... }
```

```bash
# Valid — see the [AUDIT] line printed to stdout
curl -X POST http://localhost:8081/payments \
     -H "Content-Type: application/json" \
     -d '{"id":1,"currency":"USD","amount":"49.99","reference":"TXN-001"}'

# Invalid currency — @BeforeMapping fires and returns 400
curl -X POST http://localhost:8081/payments \
     -H "Content-Type: application/json" \
     -d '{"id":2,"currency":"JPY","amount":"500","reference":"TXN-002"}'
```

---

### 7. `@InheritInverseConfiguration` — `GET /products`, `POST /products`

**Package:** `inverse/`

The forward mapping declares a rename (`description` → `summary`). Annotating the reverse method
with `@InheritInverseConfiguration` derives `summary` → `description` automatically — no need to
repeat the rename in the other direction. MapStruct has the same annotation but it breaks on record
canonical constructors when a builder is not configured; Immuto handles it natively.

```java
@Mapping(target = "summary", source = "description")
ProductDTO toDto(ProductEntity source);

@InheritInverseConfiguration(name = "toDto")   // derives description <- summary automatically
ProductEntity toEntity(ProductDTO source);
```

```bash
curl http://localhost:8081/products            # Entity → DTO, description exposed as summary

curl -X POST http://localhost:8081/products \
     -H "Content-Type: application/json" \
     -d '{"id":3,"sku":"NEW-001","summary":"Brand new item","category":"hardware"}'
```

---

### 8. Dot-notation source paths — `GET /shipments`

**Package:** `dotnotation/`

Dot-notation in `source` paths flattens a nested record into top-level DTO fields.
The processor generates a null-safe accessor chain for each segment, so a `null` intermediate
value produces `null` in the target rather than an NPE:

```java
// generated: source.origin() == null ? null : source.origin().city()
@Mapping(target = "originCity",    source = "origin.city")
@Mapping(target = "originCountry", source = "origin.country")
ShipmentDTO toDto(ShipmentEntity source);
```

The sample data includes a shipment with a `null` origin to demonstrate the null guard in action.

```bash
curl http://localhost:8081/shipments
# third entry has null originCity and originCountry — no NPE
```

---

## Project structure

```
spring-boot-advanced-features/
├── pom.xml
└── src/main/java/io/github/karunarathnad/immuto/example/advanced/
    ├── AdvancedFeaturesApplication.java
    ├── strictmapping/
    │   ├── model/        ReportEntity, ReportDTO
    │   ├── mapper/       ReportMapper.java
    │   └── controller/   ReportController.java
    ├── nested/
    │   ├── model/        OrderEntity, OrderDTO, CustomerEntity, CustomerDTO, LineItemEntity, LineItemDTO
    │   ├── mapper/       OrderMapper.java
    │   └── controller/   OrderController.java
    ├── collections/
    │   ├── model/        CatalogEntity, CatalogDTO, TagEntity, TagDTO, CategoryEntity, CategoryDTO
    │   ├── mapper/       CatalogMapper.java
    │   └── controller/   CatalogController.java
    ├── nullsafe/
    │   ├── model/        ContactEntity, ContactDTO
    │   ├── mapper/       ContactMapper.java
    │   └── controller/   ContactController.java
    ├── hooks/
    │   ├── model/        PaymentEntity, PaymentDTO
    │   ├── mapper/       PaymentMapper.java
    │   └── controller/   PaymentController.java
    ├── inverse/
    │   ├── model/        ProductEntity, ProductDTO
    │   ├── mapper/       ProductMapper.java
    │   └── controller/   ProductController.java
    └── dotnotation/
        ├── model/        ShipmentEntity, ShipmentDTO, WarehouseEntity
        ├── mapper/       ShipmentMapper.java
        └── controller/   ShipmentController.java
```

The `*MapperImpl.java` files in each `mapper/` package are generated by the Immuto annotation
processor during `mvn compile` and written to `target/generated-sources/annotations/`.

---

## Further reading

- [Spring Boot Quickstart](../spring-boot-quickstart) — basics: name-matched mapping, expressions, Spring injection
- [Immuto main README](../../Readme.md) — full annotation reference, FluentMapper API, comparison with MapStruct
- [Maven Central](https://central.sonatype.com/namespace/io.github.karunarathnad) — published artifacts
- [Issues & feedback](https://github.com/karunarathnad/immuto/issues)
