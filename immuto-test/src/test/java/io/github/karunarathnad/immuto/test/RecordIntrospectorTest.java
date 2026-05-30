package io.github.karunarathnad.immuto.test;

import io.github.karunarathnad.immuto.core.ImmutoRecordComponent;
import io.github.karunarathnad.immuto.core.MappingException;
import io.github.karunarathnad.immuto.core.RecordIntrospector;
import io.github.karunarathnad.immuto.test.fixtures.AddressDTO;
import io.github.karunarathnad.immuto.test.fixtures.AddressEntity;
import io.github.karunarathnad.immuto.test.fixtures.ProductDTO;
import io.github.karunarathnad.immuto.test.fixtures.ProductEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link RecordIntrospector} — the reflective fallback used by FluentMapper.
 */
class RecordIntrospectorTest {

    @Test
    void components_returnsInCanonicalOrder() {
        List<ImmutoRecordComponent> components = RecordIntrospector.components(ProductEntity.class);

        assertThat(components).extracting(ImmutoRecordComponent::name)
                .containsExactly("id", "name", "price", "active");
    }

    @Test
    void components_isCached() {
        List<ImmutoRecordComponent> first  = RecordIntrospector.components(ProductEntity.class);
        List<ImmutoRecordComponent> second = RecordIntrospector.components(ProductEntity.class);

        assertThat(first).isSameAs(second);
    }

    @Test
    void componentMap_isKeyedByName() {
        Map<String, ImmutoRecordComponent> map = RecordIntrospector.componentMap(ProductEntity.class);

        assertThat(map).containsKeys("id", "name", "price", "active");
    }

    @Test
    void readComponent_returnsValue() {
        ProductEntity entity = new ProductEntity(42L, "Widget", BigDecimal.TEN, true);
        ImmutoRecordComponent nameComp = RecordIntrospector.componentMap(ProductEntity.class).get("name");

        assertThat(nameComp.read(entity)).isEqualTo("Widget");
    }

    @Test
    void instantiate_callsCanonicalConstructor() {
        Object[] args = {99L, "Gadget", new BigDecimal("5.50"), false};
        ProductDTO dto = RecordIntrospector.instantiate(ProductDTO.class, args);

        assertThat(dto.id()).isEqualTo(99L);
        assertThat(dto.name()).isEqualTo("Gadget");
        assertThat(dto.price()).isEqualByComparingTo("5.50");
        assertThat(dto.active()).isFalse();
    }

    @Test
    void instantiate_throwsOnWrongArgCount() {
        assertThatThrownBy(() -> RecordIntrospector.instantiate(ProductDTO.class, new Object[]{1L}))
                .isInstanceOf(MappingException.class)
                .hasMessageContaining("expects 4 arguments");
    }

    @Test
    void shallowCopy_mapsSameNameComponents() {
        AddressEntity entity = new AddressEntity("1 Main St", "Springfield", "12345", "US");
        AddressDTO dto = RecordIntrospector.shallowCopy(entity, AddressDTO.class);

        assertThat(dto.street()).isEqualTo("1 Main St");
        assertThat(dto.city()).isEqualTo("Springfield");
        assertThat(dto.postalCode()).isEqualTo("12345");
        assertThat(dto.country()).isEqualTo("US");
    }

    @Test
    void components_throwsForNonRecord() {
        assertThatThrownBy(() -> RecordIntrospector.components(String.class))
                .isInstanceOf(MappingException.class)
                .hasMessageContaining("is not a Java record");
    }

    @Test
    void shallowCopy_nullSource_returnsNull() {
        AddressDTO result = RecordIntrospector.shallowCopy(null, AddressDTO.class);
        assertThat(result).isNull();
    }
}
