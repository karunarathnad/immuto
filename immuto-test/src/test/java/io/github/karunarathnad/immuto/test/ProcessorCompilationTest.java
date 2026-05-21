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
}
