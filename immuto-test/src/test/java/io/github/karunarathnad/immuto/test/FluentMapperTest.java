package io.github.karunarathnad.immuto.test;

import io.github.karunarathnad.immuto.core.FluentMapper;
import io.github.karunarathnad.immuto.test.fixtures.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for the runtime {@link FluentMapper} API — no annotation processor involved.
 */
class FluentMapperTest {

    @Test
    void sameNameComponents_areMappedAutomatically() {
        FluentMapper<ProductEntity, ProductDTO> mapper = FluentMapper
                .from(ProductEntity.class)
                .to(ProductDTO.class)
                .build();

        ProductEntity entity = new ProductEntity(1L, "Widget", new BigDecimal("9.99"), true);
        ProductDTO dto = mapper.map(entity);

        assertThat(dto.id()).isEqualTo(1L);
        assertThat(dto.name()).isEqualTo("Widget");
        assertThat(dto.price()).isEqualByComparingTo("9.99");
        assertThat(dto.active()).isTrue();
    }

    @Test
    void override_appliesCustomExpression() {
        // PersonEntity has extra components (internalNote) not in PersonDTO —
        // FluentMapper skips them naturally; override provides fullName.
        FluentMapper<PersonEntity, PersonDTO> mapper = FluentMapper
                .from(PersonEntity.class)
                .to(PersonDTO.class)
                .override("fullName", p -> p.firstName() + " " + p.lastName())
                .build();

        PersonEntity entity = new PersonEntity(
                10L, "Jane", "Doe", "jane@example.com",
                LocalDate.of(1990, 5, 15), null, List.of(), "secret");

        PersonDTO dto = mapper.map(entity);

        assertThat(dto.fullName()).isEqualTo("Jane Doe");
        assertThat(dto.email()).isEqualTo("jane@example.com");
        assertThat(dto.id()).isEqualTo(10L);
    }

    @Test
    void rename_mapsComponentByAlternateName() {
        // Renames source "number" → target "phoneNumber" (hypothetical rename)
        FluentMapper<PhoneEntity, PhoneDTO> mapper = FluentMapper
                .from(PhoneEntity.class)
                .to(PhoneDTO.class)
                .build();

        PhoneEntity entity = new PhoneEntity("mobile", "555-1234");
        PhoneDTO dto = mapper.map(entity);

        assertThat(dto.type()).isEqualTo("mobile");
        assertThat(dto.number()).isEqualTo("555-1234");
    }

    @Test
    void nullSource_returnsNull() {
        FluentMapper<ProductEntity, ProductDTO> mapper = FluentMapper
                .from(ProductEntity.class)
                .to(ProductDTO.class)
                .build();

        assertThat(mapper.map(null)).isNull();
    }

    @Test
    void mapAll_preservesOrderAndHandlesNullElements() {
        FluentMapper<ProductEntity, ProductDTO> mapper = FluentMapper
                .from(ProductEntity.class)
                .to(ProductDTO.class)
                .build();

        List<ProductEntity> entities = List.of(
                new ProductEntity(1L, "A", BigDecimal.ONE, true),
                new ProductEntity(2L, "B", BigDecimal.TEN, false)
        );

        List<ProductDTO> dtos = mapper.mapAll(entities);

        assertThat(dtos).hasSize(2);
        assertThat(dtos.get(0).id()).isEqualTo(1L);
        assertThat(dtos.get(1).id()).isEqualTo(2L);
    }

    @Test
    void nullList_returnsNull() {
        FluentMapper<ProductEntity, ProductDTO> mapper = FluentMapper
                .from(ProductEntity.class)
                .to(ProductDTO.class)
                .build();

        assertThat(mapper.mapAll(null)).isNull();
    }

    @Test
    void nestedRecord_withNullValue_mapsToNullComponent() {
        FluentMapper<PersonEntity, PersonDTO> mapper = FluentMapper
                .from(PersonEntity.class)
                .to(PersonDTO.class)
                .override("fullName", p -> p.firstName() + " " + p.lastName())
                .build();

        PersonEntity entity = new PersonEntity(
                1L, "John", "Doe", "john@example.com",
                LocalDate.of(1990, 1, 1), null, List.of(), null);

        PersonDTO dto = mapper.map(entity);

        assertThat(dto.address()).isNull();
        assertThat(dto.fullName()).isEqualTo("John Doe");
    }

    record PrimRecord(long id, int count, double score) {}
    record BoxedRecord(Long id, Integer count, Double score) {}

    @Test
    void primitiveToWrapper_mapsAutomatically() {
        FluentMapper<PrimRecord, BoxedRecord> mapper = FluentMapper
                .from(PrimRecord.class)
                .to(BoxedRecord.class)
                .build();

        BoxedRecord result = mapper.map(new PrimRecord(42L, 7, 3.14));

        assertThat(result.id()).isEqualTo(42L);
        assertThat(result.count()).isEqualTo(7);
        assertThat(result.score()).isEqualTo(3.14);
    }

    @Test
    void wrapperToPrimitive_mapsAutomatically() {
        FluentMapper<BoxedRecord, PrimRecord> mapper = FluentMapper
                .from(BoxedRecord.class)
                .to(PrimRecord.class)
                .build();

        PrimRecord result = mapper.map(new BoxedRecord(99L, 3, 1.5));

        assertThat(result.id()).isEqualTo(99L);
        assertThat(result.count()).isEqualTo(3);
        assertThat(result.score()).isEqualTo(1.5);
    }

    @Test
    void ignorePrimitiveComponent_usesZeroDefault() {
        // .ignore() on a primitive target component must produce the zero-value, not null
        // (passing null to Constructor.newInstance for a primitive parameter throws IAE)
        record Src(long id, String name) {}
        record Tgt(long id, String name, int count, boolean active) {}

        FluentMapper<Src, Tgt> mapper = FluentMapper
                .from(Src.class)
                .to(Tgt.class)
                .ignore("count")
                .ignore("active")
                .build();

        Tgt result = mapper.map(new Src(1L, "test"));

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("test");
        assertThat(result.count()).isEqualTo(0);
        assertThat(result.active()).isFalse();
    }

    @Test
    void mapAll_withNullElementsInList_preservesNulls() {
        FluentMapper<ProductEntity, ProductDTO> mapper = FluentMapper
                .from(ProductEntity.class)
                .to(ProductDTO.class)
                .build();

        List<ProductEntity> sources = new ArrayList<>();
        sources.add(new ProductEntity(1L, "A", BigDecimal.ONE, true));
        sources.add(null);
        sources.add(new ProductEntity(3L, "C", BigDecimal.TEN, false));

        List<ProductDTO> dtos = mapper.mapAll(sources);

        assertThat(dtos).hasSize(3);
        assertThat(dtos.get(0).id()).isEqualTo(1L);
        assertThat(dtos.get(1)).isNull();
        assertThat(dtos.get(2).id()).isEqualTo(3L);
    }
}
