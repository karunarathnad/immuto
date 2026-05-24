package io.github.karunarathnad.immuto.test;

import io.github.karunarathnad.immuto.core.MappingContext;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class MappingContextTest {

    @Test
    void empty_returnsSharedInstance() {
        assertThat(MappingContext.empty()).isSameAs(MappingContext.empty());
    }

    @Test
    void empty_isNotMutable() {
        assertThatThrownBy(() -> MappingContext.empty().put("key", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void of_storesAndRetrievesValue() {
        MappingContext ctx = MappingContext.of("tenantId", "acme");
        Optional<String> tenantId = ctx.get("tenantId");

        assertThat(tenantId).contains("acme");
    }

    @Test
    void put_chainable() {
        MappingContext ctx = MappingContext.of("a", 1);
        ctx.put("b", 2).put("c", 3);

        assertThat(ctx.<Integer>get("a")).contains(1);
        assertThat(ctx.<Integer>get("b")).contains(2);
        assertThat(ctx.<Integer>get("c")).contains(3);
    }

    @Test
    void get_absentKey_returnsEmpty() {
        MappingContext ctx = MappingContext.of("key", "value");
        assertThat(ctx.get("missing")).isEmpty();
    }

    @Test
    void contains_returnsCorrectResult() {
        MappingContext ctx = MappingContext.of("present", "yes");
        assertThat(ctx.contains("present")).isTrue();
        assertThat(ctx.contains("absent")).isFalse();
    }

    @Test
    void attributes_returnsUnmodifiableView() {
        MappingContext ctx = MappingContext.of("x", 42);
        assertThatThrownBy(() -> ctx.attributes().put("extra", "illegal"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void typedGet_returnsCorrectValue() {
        MappingContext ctx = MappingContext.of("tenantId", "acme");
        Optional<String> result = ctx.get("tenantId", String.class);
        assertThat(result).contains("acme");
    }

    @Test
    void typedGet_absentKey_returnsEmpty() {
        MappingContext ctx = MappingContext.of("key", "value");
        assertThat(ctx.get("missing", String.class)).isEmpty();
    }

    @Test
    void typedGet_wrongType_throwsClassCastException() {
        MappingContext ctx = MappingContext.of("count", 42);
        assertThatThrownBy(() -> ctx.get("count", String.class))
                .isInstanceOf(ClassCastException.class);
    }
}
