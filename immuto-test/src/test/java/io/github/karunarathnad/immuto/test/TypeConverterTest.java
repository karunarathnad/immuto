package io.github.karunarathnad.immuto.test;

import io.github.karunarathnad.immuto.core.FluentMapper;
import io.github.karunarathnad.immuto.core.MappingContext;
import io.github.karunarathnad.immuto.core.TypeConverter;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

/**
 * Verifies {@link TypeConverter} registration and invocation via {@link FluentMapper}.
 *
 * The converter use-case: source and target share the same component name but have
 * different types (BigDecimal → String). FluentMapper finds the same-named source
 * component, detects the type mismatch, and delegates to the registered converter.
 */
class TypeConverterTest {

    // Same component name "amount", different types — converter bridges the gap.
    record MoneyEntity(BigDecimal amount) {}
    record MoneyDTO(String amount) {}

    static class BigDecimalToStringConverter implements TypeConverter<BigDecimal, String> {
        @Override
        public String convert(BigDecimal source, MappingContext ctx) {
            return source == null ? null : "$" + source.toPlainString();
        }
    }

    @Test
    void customConverter_isApplied() {
        FluentMapper<MoneyEntity, MoneyDTO> mapper = FluentMapper
                .from(MoneyEntity.class)
                .to(MoneyDTO.class)
                .converter(new BigDecimalToStringConverter())
                .build();

        MoneyDTO dto = mapper.map(new MoneyEntity(new BigDecimal("12.50")));
        assertThat(dto.amount()).isEqualTo("$12.50");
    }

    @Test
    void lambdaConverter_isApplied() {
        TypeConverter<BigDecimal, String> converter =
                (src, ctx) -> src == null ? "N/A" : src.setScale(2).toPlainString();

        FluentMapper<MoneyEntity, MoneyDTO> mapper = FluentMapper
                .from(MoneyEntity.class)
                .to(MoneyDTO.class)
                .converter(converter)
                .build();

        assertThat(mapper.map(new MoneyEntity(null)).amount()).isEqualTo("N/A");
        assertThat(mapper.map(new MoneyEntity(new BigDecimal("5"))).amount()).isEqualTo("5.00");
    }

    record IntEntity(int code) {}
    record IntDTO(String code) {}

    @Test
    void typedConverter_onlyInvokedForMatchingSourceType() {
        // Register a String→String converter and a BigDecimal→String converter.
        // The BD converter must not be invoked for the String value.
        TypeConverter<String, String> stringConverter = (src, ctx) -> "str:" + src;
        TypeConverter<BigDecimal, String> bdConverter = (src, ctx) -> "bd:" + src.toPlainString();

        record Dual(String label, BigDecimal amount) {}
        record DualDTO(String label, String amount) {}

        FluentMapper<Dual, DualDTO> mapper = FluentMapper
                .from(Dual.class)
                .to(DualDTO.class)
                .converter(String.class, stringConverter)
                .converter(BigDecimal.class, bdConverter)
                .build();

        DualDTO dto = mapper.map(new Dual("hello", new BigDecimal("7.5")));
        assertThat(dto.label()).isEqualTo("str:hello");
        assertThat(dto.amount()).isEqualTo("bd:7.5");
    }

    @Test
    void typedConverter_internalClassCastException_propagates() {
        TypeConverter<BigDecimal, String> buggyConverter = (src, ctx) -> {
            // Simulated internal bug: incorrect cast inside converter body
            @SuppressWarnings("unchecked")
            java.util.List<String> wrongCast = (java.util.List<String>) (Object) src;
            return wrongCast.get(0);
        };

        FluentMapper<MoneyEntity, MoneyDTO> mapper = FluentMapper
                .from(MoneyEntity.class)
                .to(MoneyDTO.class)
                .converter(BigDecimal.class, buggyConverter)
                .build();

        assertThatThrownBy(() -> mapper.map(new MoneyEntity(new BigDecimal("5"))))
                .isInstanceOf(ClassCastException.class);
    }

    @Test
    void converterWithContext_canAccessContextValues() {
        TypeConverter<BigDecimal, String> converter = (src, ctx) -> {
            String currency = ctx.<String>get("currency").orElse("USD");
            return currency + " " + (src == null ? "0" : src.toPlainString());
        };

        FluentMapper<MoneyEntity, MoneyDTO> mapper = FluentMapper
                .from(MoneyEntity.class)
                .to(MoneyDTO.class)
                .converter(converter)
                .build();

        MappingContext ctx = MappingContext.of("currency", "EUR");
        MoneyDTO dto = mapper.map(new MoneyEntity(new BigDecimal("99")), ctx);
        assertThat(dto.amount()).isEqualTo("EUR 99");
    }
}
