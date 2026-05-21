package io.github.karunarathnad.immuto.processor.generator;

import io.github.karunarathnad.immuto.processor.model.MapperMethodModel;
import io.github.karunarathnad.immuto.processor.model.MapperModel;
import io.github.karunarathnad.immuto.processor.model.MappingModel;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Modifier;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;

/**
 * Generates the {@code *Impl} Java source file for a single {@link MapperModel}.
 *
 * <p>The generated class:
 * <ul>
 *   <li>Is {@code @Generated} with the processor class name and current date</li>
 *   <li>Implements the user's {@code @RecordMapper} interface</li>
 *   <li>Also implements {@link io.github.karunarathnad.immuto.core.ImmutoMapper}</li>
 *   <li>Calls each target record's <em>canonical constructor</em> — never a setter</li>
 *   <li>Handles {@code null} source by early-returning {@code null}</li>
 *   <li>Wraps results in {@code Optional.ofNullable} for {@code @NullSafe} methods</li>
 * </ul>
 */
public final class MapperCodeGenerator {

    private static final String PROCESSOR = "io.github.karunarathnad.immuto.processor.RecordMapperProcessor";

    private final Filer filer;

    public MapperCodeGenerator(Filer filer) {
        this.filer = filer;
    }

    public void generate(MapperModel model) throws IOException {
        String fqImplName = model.packageName() + "." + model.implClassName();
        JavaFileObject file = filer.createSourceFile(fqImplName);

        try (PrintWriter w = new PrintWriter(file.openWriter())) {
            writePackage(w, model);
            writeImports(w, model);
            writeClassHeader(w, model);
            for (MapperMethodModel method : model.methods()) {
                writeMethod(w, method);
            }
            w.println("}");
        }
    }

    // -------------------------------------------------------------------------

    private void writePackage(PrintWriter w, MapperModel model) {
        if (!model.packageName().isEmpty()) {
            w.println("package " + model.packageName() + ";");
            w.println();
        }
    }

    private void writeImports(PrintWriter w, MapperModel model) {
        w.println("import io.github.karunarathnad.immuto.core.ImmutoMapper;");
        w.println("import javax.annotation.processing.Generated;");
        boolean needsOptional = model.methods().stream().anyMatch(MapperMethodModel::isNullSafe);
        if (needsOptional) {
            w.println("import java.util.Optional;");
        }
        boolean needsList = model.methods().stream()
                .flatMap(m -> m.componentMappings().stream())
                .anyMatch(cm -> cm.sourceExpression().contains("stream()"));
        if (needsList) {
            w.println("import java.util.List;");
            w.println("import java.util.stream.Collectors;");
        }
        w.println();
    }

    private void writeClassHeader(PrintWriter w, MapperModel model) {
        w.println("@Generated(");
        w.println("    value = \"" + PROCESSOR + "\",");
        w.println("    date = \"" + LocalDate.now() + "\"");
        w.println(")");
        w.println("public final class " + model.implClassName());
        w.println("        implements " + model.interfaceSimpleName() + ", ImmutoMapper {");
        w.println();
    }

    private void writeMethod(PrintWriter w, MapperMethodModel method) {
        String src = method.sourceParamName();
        String targetFqn = method.targetTypeFqn();
        String returnType = method.isNullSafe()
                ? "Optional<" + method.targetSimpleName() + ">"
                : method.targetSimpleName();

        w.println("    @Override");
        w.println("    public " + returnType + " " + method.methodName()
                + "(" + method.sourceTypeFqn() + " " + src + ") {");

        // null guard
        if (method.isNullSafe()) {
            w.println("        if (" + src + " == null) return Optional.empty();");
        } else {
            w.println("        if (" + src + " == null) return null;");
        }
        w.println();

        // @BeforeMapping hook
        if (method.hasBeforeMapping()) {
            w.println("        beforeMapping(" + src + ");");
        }

        // build constructor call
        w.println("        " + method.targetSimpleName() + " __result = new " + method.targetSimpleName() + "(");
        var mappings = method.componentMappings();
        for (int i = 0; i < mappings.size(); i++) {
            MappingModel cm = mappings.get(i);
            String comma = (i < mappings.size() - 1) ? "," : "";
            w.println("            " + cm.sourceExpression() + comma
                    + "  // -> " + cm.targetComponent());
        }
        w.println("        );");
        w.println();

        // @AfterMapping hook
        if (method.hasAfterMapping()) {
            w.println("        afterMapping(" + src + ", __result);");
        }

        if (method.isNullSafe()) {
            w.println("        return Optional.of(__result);");
        } else {
            w.println("        return __result;");
        }
        w.println("    }");
        w.println();
    }
}
