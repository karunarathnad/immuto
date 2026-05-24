package io.github.karunarathnad.immuto.test;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import io.github.karunarathnad.immuto.processor.RecordMapperProcessor;
import org.junit.jupiter.api.Test;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

/**
 * Compile-time tests for the annotation processor.
 * Verifies that valid mappers produce the expected Impl class and that
 * invalid inputs produce clear compile-time error messages.
 *
 * Note: compile-testing uses Google Truth, whose StringSubject.contains() returns void —
 * each content assertion must be a separate statement.
 */
class ProcessorCompilationTest {

    @Test
    void simpleRecordMapper_compilesSuccessfully() {
        Compilation compilation = javac()
                .withProcessors(new RecordMapperProcessor())
                .compile(
                        JavaFileObjects.forSourceString("test.Item",
                                """
                                package test;
                                public record Item(Long id, String name) {}
                                """),
                        JavaFileObjects.forSourceString("test.ItemDTO",
                                """
                                package test;
                                public record ItemDTO(Long id, String name) {}
                                """),
                        JavaFileObjects.forSourceString("test.ItemMapper",
                                """
                                package test;
                                import io.github.karunarathnad.immuto.annotation.RecordMapper;
                                @RecordMapper
                                public interface ItemMapper {
                                    ItemDTO toDto(Item source);
                                }
                                """)
                );

        assertThat(compilation).succeededWithoutWarnings();
        assertThat(compilation).generatedSourceFile("test.ItemMapperImpl");
    }

    @Test
    void generatedImpl_containsCanonicalConstructorCall() {
        Compilation compilation = javac()
                .withProcessors(new RecordMapperProcessor())
                .compile(
                        JavaFileObjects.forSourceString("test.Box",
                                """
                                package test;
                                public record Box(Long id, String label) {}
                                """),
                        JavaFileObjects.forSourceString("test.BoxDTO",
                                """
                                package test;
                                public record BoxDTO(Long id, String label) {}
                                """),
                        JavaFileObjects.forSourceString("test.BoxMapper",
                                """
                                package test;
                                import io.github.karunarathnad.immuto.annotation.RecordMapper;
                                @RecordMapper
                                public interface BoxMapper {
                                    BoxDTO toDto(Box source);
                                }
                                """)
                );

        assertThat(compilation).succeeded();
        var subject = assertThat(compilation).generatedSourceFile("test.BoxMapperImpl")
                .contentsAsUtf8String();
        subject.contains("new BoxDTO(");
        subject.contains("source.id()");
        subject.contains("source.label()");
    }

    @Test
    void expressionMapping_isInlinedIntoConstructorCall() {
        Compilation compilation = javac()
                .withProcessors(new RecordMapperProcessor())
                .compile(
                        JavaFileObjects.forSourceString("test.Person",
                                """
                                package test;
                                public record Person(String firstName, String lastName) {}
                                """),
                        JavaFileObjects.forSourceString("test.PersonView",
                                """
                                package test;
                                public record PersonView(String fullName) {}
                                """),
                        JavaFileObjects.forSourceString("test.PersonMapper",
                                """
                                package test;
                                import io.github.karunarathnad.immuto.annotation.*;
                                @RecordMapper
                                public interface PersonMapper {
                                    @Mapping(target = "fullName",
                                             expression = "java(source.firstName() + \\" \\" + source.lastName())")
                                    PersonView toView(Person source);
                                }
                                """)
                );

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("test.PersonMapperImpl")
                .contentsAsUtf8String()
                .contains("source.firstName() + \" \" + source.lastName()");
    }

    @Test
    void annotatingClass_producesCompileError() {
        Compilation compilation = javac()
                .withProcessors(new RecordMapperProcessor())
                .compile(
                        JavaFileObjects.forSourceString("test.BadMapper",
                                """
                                package test;
                                import io.github.karunarathnad.immuto.annotation.RecordMapper;
                                @RecordMapper
                                public class BadMapper {}
                                """)
                );

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("must annotate an interface");
    }

    @Test
    void unmappedTargetComponent_producesCompileError() {
        Compilation compilation = javac()
                .withProcessors(new RecordMapperProcessor())
                .compile(
                        JavaFileObjects.forSourceString("test.Src",
                                """
                                package test;
                                public record Src(Long id) {}
                                """),
                        JavaFileObjects.forSourceString("test.Tgt",
                                """
                                package test;
                                public record Tgt(Long id, String extra) {}
                                """),
                        JavaFileObjects.forSourceString("test.SrcMapper",
                                """
                                package test;
                                import io.github.karunarathnad.immuto.annotation.RecordMapper;
                                @RecordMapper
                                public interface SrcMapper {
                                    Tgt toTgt(Src source);
                                }
                                """)
                );

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("extra");
    }

    @Test
    void constantMapping_isLiteralInGeneratedCode() {
        Compilation compilation = javac()
                .withProcessors(new RecordMapperProcessor())
                .compile(
                        JavaFileObjects.forSourceString("test.Widget",
                                """
                                package test;
                                public record Widget(Long id) {}
                                """),
                        JavaFileObjects.forSourceString("test.WidgetDTO",
                                """
                                package test;
                                public record WidgetDTO(Long id, boolean active) {}
                                """),
                        JavaFileObjects.forSourceString("test.WidgetMapper",
                                """
                                package test;
                                import io.github.karunarathnad.immuto.annotation.*;
                                @RecordMapper
                                public interface WidgetMapper {
                                    @Mapping(target = "active", constant = "true")
                                    WidgetDTO toDto(Widget source);
                                }
                                """)
                );

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("test.WidgetMapperImpl")
                .contentsAsUtf8String()
                .contains("true");
    }

    @Test
    void springComponentModel_generatesAtComponentAnnotation() {
        Compilation compilation = javac()
                .withProcessors(new RecordMapperProcessor())
                .compile(
                        // Stub @Component so javac can resolve it without a Spring dependency
                        JavaFileObjects.forSourceString("org.springframework.stereotype.Component",
                                """
                                package org.springframework.stereotype;
                                import java.lang.annotation.*;
                                @Target(ElementType.TYPE) @Retention(RetentionPolicy.RUNTIME)
                                public @interface Component { String value() default ""; }
                                """),
                        JavaFileObjects.forSourceString("test.Thing",
                                """
                                package test;
                                public record Thing(Long id) {}
                                """),
                        JavaFileObjects.forSourceString("test.ThingDTO",
                                """
                                package test;
                                public record ThingDTO(Long id) {}
                                """),
                        JavaFileObjects.forSourceString("test.ThingMapper",
                                """
                                package test;
                                import io.github.karunarathnad.immuto.annotation.RecordMapper;
                                @RecordMapper(componentModel = "spring")
                                public interface ThingMapper {
                                    ThingDTO toDto(Thing source);
                                }
                                """)
                );

        assertThat(compilation).succeededWithoutWarnings();
        var generatedSource = assertThat(compilation).generatedSourceFile("test.ThingMapperImpl")
                .contentsAsUtf8String();
        generatedSource.contains("@Component");
        generatedSource.contains("import org.springframework.stereotype.Component;");
    }

    @Test
    void setMapping_generatesStreamCollectToUnmodifiableSet() {
        Compilation compilation = javac()
                .withProcessors(new RecordMapperProcessor())
                .compile(
                        JavaFileObjects.forSourceString("test.TagEntity",
                                """
                                package test;
                                public record TagEntity(String value) {}
                                """),
                        JavaFileObjects.forSourceString("test.TagDTO",
                                """
                                package test;
                                public record TagDTO(String value) {}
                                """),
                        JavaFileObjects.forSourceString("test.Article",
                                """
                                package test;
                                import java.util.Set;
                                public record Article(String title, Set<TagEntity> tags) {}
                                """),
                        JavaFileObjects.forSourceString("test.ArticleDTO",
                                """
                                package test;
                                import java.util.Set;
                                public record ArticleDTO(String title, Set<TagDTO> tags) {}
                                """),
                        JavaFileObjects.forSourceString("test.ArticleMapper",
                                """
                                package test;
                                import io.github.karunarathnad.immuto.annotation.RecordMapper;
                                @RecordMapper
                                public interface ArticleMapper {
                                    ArticleDTO toDto(Article source);
                                }
                                """)
                );

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("test.ArticleMapperImpl")
                .contentsAsUtf8String()
                .contains("toUnmodifiableSet()");
    }

    @Test
    void nullSafeMapping_returnsOptional() {
        Compilation compilation = javac()
                .withProcessors(new RecordMapperProcessor())
                .compile(
                        JavaFileObjects.forSourceString("test.Addr",
                                """
                                package test;
                                public record Addr(String city) {}
                                """),
                        JavaFileObjects.forSourceString("test.AddrDTO",
                                """
                                package test;
                                public record AddrDTO(String city) {}
                                """),
                        JavaFileObjects.forSourceString("test.AddrMapper",
                                """
                                package test;
                                import java.util.Optional;
                                import io.github.karunarathnad.immuto.annotation.*;
                                @RecordMapper
                                public interface AddrMapper {
                                    @NullSafe
                                    Optional<AddrDTO> toDto(Addr source);
                                }
                                """)
                );

        assertThat(compilation).succeeded();
        var src = assertThat(compilation).generatedSourceFile("test.AddrMapperImpl")
                .contentsAsUtf8String();
        src.contains("Optional<AddrDTO>");
        src.contains("Optional.empty()");
        src.contains("Optional.of(");
    }

    @Test
    void ignoredMapping_suppressesUnmappedError() {
        Compilation compilation = javac()
                .withProcessors(new RecordMapperProcessor())
                .compile(
                        JavaFileObjects.forSourceString("test.Src",
                                """
                                package test;
                                public record Src(Long id) {}
                                """),
                        JavaFileObjects.forSourceString("test.Tgt",
                                """
                                package test;
                                public record Tgt(Long id, String extra) {}
                                """),
                        JavaFileObjects.forSourceString("test.SrcMapper",
                                """
                                package test;
                                import io.github.karunarathnad.immuto.annotation.*;
                                @RecordMapper
                                public interface SrcMapper {
                                    @Mapping(target = "extra", ignore = true)
                                    Tgt toTgt(Src source);
                                }
                                """)
                );

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("test.SrcMapperImpl")
                .contentsAsUtf8String()
                .contains("null");
    }

    @Test
    void listRecordMapping_generatesStreamCollect() {
        Compilation compilation = javac()
                .withProcessors(new RecordMapperProcessor())
                .compile(
                        JavaFileObjects.forSourceString("test.TagEntity",
                                """
                                package test;
                                public record TagEntity(String value) {}
                                """),
                        JavaFileObjects.forSourceString("test.TagDTO",
                                """
                                package test;
                                public record TagDTO(String value) {}
                                """),
                        JavaFileObjects.forSourceString("test.Post",
                                """
                                package test;
                                import java.util.List;
                                public record Post(String title, List<TagEntity> tags) {}
                                """),
                        JavaFileObjects.forSourceString("test.PostDTO",
                                """
                                package test;
                                import java.util.List;
                                public record PostDTO(String title, List<TagDTO> tags) {}
                                """),
                        JavaFileObjects.forSourceString("test.PostMapper",
                                """
                                package test;
                                import io.github.karunarathnad.immuto.annotation.RecordMapper;
                                @RecordMapper
                                public interface PostMapper {
                                    PostDTO toDto(Post source);
                                }
                                """)
                );

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("test.PostMapperImpl")
                .contentsAsUtf8String()
                .contains("toUnmodifiableList()");
    }

    @Test
    void inheritInverseConfiguration_happyPath() {
        Compilation compilation = javac()
                .withProcessors(new RecordMapperProcessor())
                .compile(
                        JavaFileObjects.forSourceString("test.Order",
                                """
                                package test;
                                public record Order(Long id, String ref) {}
                                """),
                        JavaFileObjects.forSourceString("test.OrderDTO",
                                """
                                package test;
                                public record OrderDTO(Long id, String ref) {}
                                """),
                        JavaFileObjects.forSourceString("test.OrderMapper",
                                """
                                package test;
                                import io.github.karunarathnad.immuto.annotation.*;
                                @RecordMapper
                                public interface OrderMapper {
                                    OrderDTO toDto(Order source);
                                    @InheritInverseConfiguration(name = "toDto")
                                    Order toEntity(OrderDTO source);
                                }
                                """)
                );

        assertThat(compilation).succeededWithoutWarnings();
        assertThat(compilation).generatedSourceFile("test.OrderMapperImpl")
                .contentsAsUtf8String()
                .contains("toEntity");
    }

    @Test
    void typeMismatch_withoutConverter_producesCompileError() {
        Compilation compilation = javac()
                .withProcessors(new RecordMapperProcessor())
                .compile(
                        JavaFileObjects.forSourceString("test.Event",
                                """
                                package test;
                                import java.time.Instant;
                                public record Event(Long id, Instant createdAt) {}
                                """),
                        JavaFileObjects.forSourceString("test.EventDTO",
                                """
                                package test;
                                public record EventDTO(Long id, String createdAt) {}
                                """),
                        JavaFileObjects.forSourceString("test.EventMapper",
                                """
                                package test;
                                import io.github.karunarathnad.immuto.annotation.RecordMapper;
                                @RecordMapper
                                public interface EventMapper {
                                    EventDTO toDto(Event source);
                                }
                                """)
                );

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("createdAt");
    }

    @Test
    void dotNotationSource_resolvesNestedAccessor() {
        Compilation compilation = javac()
                .withProcessors(new RecordMapperProcessor())
                .compile(
                        JavaFileObjects.forSourceString("test.Address",
                                """
                                package test;
                                public record Address(String city) {}
                                """),
                        JavaFileObjects.forSourceString("test.Customer",
                                """
                                package test;
                                public record Customer(Long id, Address address) {}
                                """),
                        JavaFileObjects.forSourceString("test.CustomerDTO",
                                """
                                package test;
                                public record CustomerDTO(Long id, String city) {}
                                """),
                        JavaFileObjects.forSourceString("test.CustomerMapper",
                                """
                                package test;
                                import io.github.karunarathnad.immuto.annotation.*;
                                @RecordMapper
                                public interface CustomerMapper {
                                    @Mapping(target = "city", source = "address.city")
                                    CustomerDTO toDto(Customer source);
                                }
                                """)
                );

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("test.CustomerMapperImpl")
                .contentsAsUtf8String()
                .contains("source.address().city()");
    }

    @Test
    void inheritInverseWithExpression_producesCompileError() {
        Compilation compilation = javac()
                .withProcessors(new RecordMapperProcessor())
                .compile(
                        JavaFileObjects.forSourceString("test.PersonEntity",
                                """
                                package test;
                                public record PersonEntity(String firstName, String lastName) {}
                                """),
                        JavaFileObjects.forSourceString("test.PersonDTO",
                                """
                                package test;
                                public record PersonDTO(String fullName) {}
                                """),
                        JavaFileObjects.forSourceString("test.PersonMapper",
                                """
                                package test;
                                import io.github.karunarathnad.immuto.annotation.*;
                                @RecordMapper
                                public interface PersonMapper {
                                    @Mapping(target = "fullName",
                                             expression = "java(source.firstName() + \\" \\" + source.lastName())")
                                    PersonDTO toDto(PersonEntity source);

                                    @InheritInverseConfiguration(name = "toDto")
                                    PersonEntity toEntity(PersonDTO source);
                                }
                                """)
                );

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("cannot auto-invert");
    }
}
