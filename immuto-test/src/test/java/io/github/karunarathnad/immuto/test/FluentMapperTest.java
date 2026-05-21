package io.github.karunarathnad.immuto.test;

import io.github.karunarathnad.immuto.core.FluentMapper;
import io.github.karunarathnad.immuto.test.fixtures.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
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
}
