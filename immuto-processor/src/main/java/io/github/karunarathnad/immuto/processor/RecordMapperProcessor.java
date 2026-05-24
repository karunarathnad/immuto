package io.github.karunarathnad.immuto.processor;

import com.google.auto.service.AutoService;
import io.github.karunarathnad.immuto.annotation.*;
import io.github.karunarathnad.immuto.processor.generator.MapperCodeGenerator;
import io.github.karunarathnad.immuto.processor.model.*;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Annotation processor for {@link RecordMapper}-annotated interfaces.
 *
 * <p>For every annotated interface the processor:
 * <ol>
 *   <li>Validates that all method return types and parameters are Java records</li>
 *   <li>Resolves per-component mappings, honouring {@link Mapping} overrides</li>
 *   <li>Detects {@link InheritInverseConfiguration} and generates the reverse</li>
 *   <li>Emits a {@code *Impl} source file via {@link MapperCodeGenerator}</li>
 * </ol>
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes("io.github.karunarathnad.immuto.annotation.RecordMapper")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class RecordMapperProcessor extends AbstractProcessor {

    private Elements elementUtils;
    private Types typeUtils;
    private Messager messager;
    private MapperCodeGenerator generator;

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        elementUtils = env.getElementUtils();
        typeUtils    = env.getTypeUtils();
        messager     = env.getMessager();
        generator    = new MapperCodeGenerator(env.getFiler());
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        for (Element element : round.getElementsAnnotatedWith(RecordMapper.class)) {
            if (element.getKind() != ElementKind.INTERFACE) {
                error(element, "@RecordMapper must annotate an interface, found: " + element.getKind());
                continue;
            }
            try {
                processMapper((TypeElement) element);
            } catch (Exception e) {
                error(element, "Immuto processing failed: " + e.getMessage());
            }
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // Per-mapper processing
    // -------------------------------------------------------------------------

    private void processMapper(TypeElement iface) throws IOException {
        RecordMapper annotation = iface.getAnnotation(RecordMapper.class);
        String packageName = elementUtils.getPackageOf(iface).getQualifiedName().toString();
        String simpleName  = iface.getSimpleName().toString();
        String implName    = simpleName + "Impl";

        List<ExecutableElement> methods = collectAbstractMethods(iface);
        List<MapperMethodModel> methodModels = new ArrayList<>();

        for (ExecutableElement method : methods) {
            MapperMethodModel model = processMethod(iface, method, methods, annotation);
            if (model != null) methodModels.add(model);
        }

        MapperModel mapperModel = new MapperModel(packageName, simpleName, implName, methodModels,
                annotation.componentModel());
        generator.generate(mapperModel);
    }

    // -------------------------------------------------------------------------
    // Per-method processing
    // -------------------------------------------------------------------------

    private MapperMethodModel processMethod(
            TypeElement iface,
            ExecutableElement method,
            List<ExecutableElement> allMethods,
            RecordMapper annotation) {

        if (method.getParameters().size() != 1) {
            error(method, "Mapper method must have exactly one parameter");
            return null;
        }

        TypeMirror returnMirror = method.getReturnType();
        TypeMirror sourceMirror = method.getParameters().get(0).asType();
        String sourceParamName  = method.getParameters().get(0).getSimpleName().toString();

        // Unwrap Optional<T> for @NullSafe methods
        boolean isNullSafe = method.getAnnotation(NullSafe.class) != null;
        TypeMirror targetMirror = isNullSafe ? unwrapOptional(returnMirror, method) : returnMirror;
        if (targetMirror == null) return null;

        TypeElement targetElement = asTypeElement(targetMirror);
        TypeElement sourceElement = asTypeElement(sourceMirror);

        if (targetElement == null) {
            error(method, "Cannot resolve target type: " + targetMirror);
            return null;
        }
        if (!isRecord(targetElement)) {
            error(method, "Target type must be a Java record: " + targetElement.getQualifiedName());
            return null;
        }
        if (sourceElement == null || !isRecord(sourceElement)) {
            error(method, "Source parameter type must be a Java record: " + sourceMirror);
            return null;
        }

        // Collect all @Mapping annotations on this method
        Mapping[] mappingAnnotations = method.getAnnotationsByType(Mapping.class);
        Map<String, Mapping> mappingByTarget = new LinkedHashMap<>();
        for (Mapping m : mappingAnnotations) mappingByTarget.put(m.target(), m);

        // Handle @InheritInverseConfiguration
        InheritInverseConfiguration inherit = method.getAnnotation(InheritInverseConfiguration.class);
        if (inherit != null) {
            return buildInverseMethod(method, inherit, allMethods, sourceElement,
                    targetElement, sourceParamName, isNullSafe);
        }

        // Build component mappings for target record
        List<RecordComponentElement> targetComponents = new ArrayList<>(targetElement.getRecordComponents());
        Map<String, RecordComponentElement> sourceComponentMap = new ArrayList<>(sourceElement.getRecordComponents())
                .stream().collect(Collectors.toMap(c -> c.getSimpleName().toString(), c -> c));

        boolean warnUnmapped = annotation.warnOnUnmappedTargetComponents();
        List<MappingModel> componentMappings = new ArrayList<>();

        for (RecordComponentElement targetComp : targetComponents) {
            String compName = targetComp.getSimpleName().toString();
            MappingModel mappingModel = resolveComponentMapping(
                    compName, targetComp, sourceComponentMap, mappingByTarget,
                    sourceParamName, method, warnUnmapped);
            if (mappingModel != null) componentMappings.add(mappingModel);
        }

        boolean hasBefore = iface.getEnclosedElements().stream()
                .anyMatch(e -> e instanceof ExecutableElement ee
                        && ee.getAnnotation(BeforeMapping.class) != null
                        && matchesSourceParam(ee, sourceMirror));
        boolean hasAfter = iface.getEnclosedElements().stream()
                .anyMatch(e -> e instanceof ExecutableElement ee
                        && ee.getAnnotation(AfterMapping.class) != null
                        && matchesSourceAndTargetParams(ee, sourceMirror, targetMirror));

        return new MapperMethodModel(
                method.getSimpleName().toString(),
                sourceParamName,
                sourceElement.getQualifiedName().toString(),
                targetElement.getQualifiedName().toString(),
                targetElement.getSimpleName().toString(),
                componentMappings,
                hasBefore,
                hasAfter,
                isNullSafe
        );
    }

    // -------------------------------------------------------------------------
    // Component-level mapping resolution
    // -------------------------------------------------------------------------

    private MappingModel resolveComponentMapping(
            String compName,
            RecordComponentElement targetComp,
            Map<String, RecordComponentElement> sourceMap,
            Map<String, Mapping> overrides,
            String sourceParam,
            ExecutableElement method,
            boolean warnUnmapped) {

        Mapping override = overrides.get(compName);

        if (override != null) {
            if (override.ignore()) {
                return MappingModel.ignored(compName);
            }
            if (!override.expression().isEmpty()) {
                // strip the "java(...)" wrapper if present
                String expr = override.expression().trim();
                if (expr.startsWith("java(") && expr.endsWith(")")) {
                    expr = expr.substring(5, expr.length() - 1);
                }
                return MappingModel.direct(compName, expr);
            }
            if (!override.constant().isEmpty()) {
                return MappingModel.direct(compName,
                        constantExpression(override.constant(), targetComp.asType()));
            }
            if (!override.source().isEmpty()) {
                // dot-notation: "address.city" → source.address().city()
                return MappingModel.direct(compName,
                        dotChain(sourceParam, override.source()));
            }
        }

        // default: same-name lookup
        RecordComponentElement sourceComp = sourceMap.get(compName);
        if (sourceComp != null) {
            TypeMirror srcType = sourceComp.asType();
            TypeMirror tgtType = targetComp.asType();

            if (typeUtils.isAssignable(srcType, tgtType)) {
                return MappingModel.direct(compName, sourceParam + "." + compName + "()");
            }

            // Both records → nested mapping via a helper expression
            TypeElement srcElem = asTypeElement(srcType);
            TypeElement tgtElem = asTypeElement(tgtType);
            if (srcElem != null && isRecord(srcElem) && tgtElem != null && isRecord(tgtElem)) {
                // Inline shallow copy via RecordIntrospector
                return MappingModel.direct(compName,
                        "io.github.karunarathnad.immuto.core.RecordIntrospector.shallowCopy("
                                + sourceParam + "." + compName + "(), "
                                + tgtElem.getQualifiedName() + ".class)");
            }

            // List<Record> → mapped list
            if (isList(srcType) && isList(tgtType)) {
                TypeMirror srcElemType = typeArgument(srcType, 0);
                TypeMirror tgtElemType = typeArgument(tgtType, 0);
                TypeElement srcListElem = asTypeElement(srcElemType);
                TypeElement tgtListElem = asTypeElement(tgtElemType);
                if (srcListElem != null && isRecord(srcListElem)
                        && tgtListElem != null && isRecord(tgtListElem)) {
                    return MappingModel.direct(compName,
                            sourceParam + "." + compName + "() == null ? null : "
                                    + sourceParam + "." + compName + "().stream()"
                                    + ".map(e -> io.github.karunarathnad.immuto.core.RecordIntrospector"
                                    + ".shallowCopy(e, " + tgtListElem.getQualifiedName() + ".class))"
                                    + ".collect(java.util.stream.Collectors.toUnmodifiableList())");
                }
            }

            // Set<Record> → mapped set
            if (isSet(srcType) && isSet(tgtType)) {
                TypeMirror srcElemType = typeArgument(srcType, 0);
                TypeMirror tgtElemType = typeArgument(tgtType, 0);
                TypeElement srcSetElem = asTypeElement(srcElemType);
                TypeElement tgtSetElem = asTypeElement(tgtElemType);
                if (srcSetElem != null && isRecord(srcSetElem)
                        && tgtSetElem != null && isRecord(tgtSetElem)) {
                    return MappingModel.direct(compName,
                            sourceParam + "." + compName + "() == null ? null : "
                                    + sourceParam + "." + compName + "().stream()"
                                    + ".map(e -> io.github.karunarathnad.immuto.core.RecordIntrospector"
                                    + ".shallowCopy(e, " + tgtSetElem.getQualifiedName() + ".class))"
                                    + ".collect(java.util.stream.Collectors.toUnmodifiableSet())");
                }
            }

            // Map<K, Record> → mapped map (keys pass through, values are converted)
            if (isMap(srcType) && isMap(tgtType)) {
                TypeMirror srcValType = typeArgument(srcType, 1);
                TypeMirror tgtValType = typeArgument(tgtType, 1);
                TypeElement srcMapVal = asTypeElement(srcValType);
                TypeElement tgtMapVal = asTypeElement(tgtValType);
                if (srcMapVal != null && isRecord(srcMapVal)
                        && tgtMapVal != null && isRecord(tgtMapVal)) {
                    return MappingModel.direct(compName,
                            sourceParam + "." + compName + "() == null ? null : "
                                    + sourceParam + "." + compName + "().entrySet().stream()"
                                    + ".collect(java.util.stream.Collectors.toUnmodifiableMap("
                                    + "java.util.Map.Entry::getKey, "
                                    + "e -> io.github.karunarathnad.immuto.core.RecordIntrospector"
                                    + ".shallowCopy(e.getValue(), " + tgtMapVal.getQualifiedName() + ".class)))");
                }
            }

            // Optional<Record> unwrap/wrap
            if (isOptional(srcType) && isOptional(tgtType)) {
                return MappingModel.direct(compName, sourceParam + "." + compName + "()");
            }

            error(method, "Cannot auto-convert component '" + compName + "': "
                    + srcType + " → " + tgtType
                    + ". Provide an explicit @Mapping or a TypeConverter.");
            return MappingModel.ignored(compName);
        }

        // Not found in source
        if (warnUnmapped) {
            warn(method, "Target component '" + compName + "' has no matching source component.");
        } else {
            error(method, "Target component '" + compName + "' has no matching source component. "
                    + "Use @Mapping(target=\"" + compName + "\", ignore=true) to suppress this error.");
        }
        return MappingModel.ignored(compName);
    }

    // -------------------------------------------------------------------------
    // Inverse configuration
    // -------------------------------------------------------------------------

    private MapperMethodModel buildInverseMethod(
            ExecutableElement method,
            InheritInverseConfiguration inherit,
            List<ExecutableElement> allMethods,
            TypeElement sourceElement,
            TypeElement targetElement,
            String sourceParamName,
            boolean isNullSafe) {

        String forwardName = inherit.name();
        Optional<ExecutableElement> forwardMethod = allMethods.stream()
                .filter(m -> !m.equals(method))
                .filter(m -> {
                    if (!forwardName.isEmpty()) return m.getSimpleName().toString().equals(forwardName);
                    // auto-find: return type of forward == our source, param of forward == our target
                    if (m.getParameters().size() != 1) return false;
                    TypeMirror fwdReturn = m.getReturnType();
                    TypeMirror fwdParam  = m.getParameters().get(0).asType();
                    return typeUtils.isSameType(fwdReturn, sourceElement.asType())
                            || typeUtils.isSameType(fwdParam, targetElement.asType());
                })
                .findFirst();

        if (forwardMethod.isEmpty()) {
            error(method, "@InheritInverseConfiguration: cannot find the forward mapping method.");
            return null;
        }

        ExecutableElement fwd = forwardMethod.get();
        Mapping[] fwdMappings = fwd.getAnnotationsByType(Mapping.class);

        // invert: source/target swap for each Mapping that used explicit source/target names
        List<MappingModel> invertedMappings = new ArrayList<>();
        TypeElement newSource = asTypeElement(method.getParameters().get(0).asType());
        TypeElement newTarget = asTypeElement(method.getReturnType());

        if (newSource == null || newTarget == null) {
            error(method, "@InheritInverseConfiguration: cannot resolve inverted source/target types");
            return null;
        }

        Map<String, RecordComponentElement> newSourceMap = new ArrayList<>(newSource.getRecordComponents())
                .stream().collect(Collectors.toMap(c -> c.getSimpleName().toString(), c -> c));

        // Expression-mapped forward targets cannot be automatically inverted
        Set<String> expressionMappedTargets = Arrays.stream(fwdMappings)
                .filter(m -> !m.expression().isEmpty())
                .map(Mapping::target)
                .collect(Collectors.toSet());

        for (RecordComponentElement targetComp : new ArrayList<>(newTarget.getRecordComponents())) {
            String compName = targetComp.getSimpleName().toString();

            // Find a forward mapping that had this as its source → now it's our target
            Optional<Mapping> matchedForward = Arrays.stream(fwdMappings)
                    .filter(m -> m.source().equals(compName))
                    .findFirst();

            if (matchedForward.isPresent()) {
                String fwdTarget = matchedForward.get().target();
                if (newSourceMap.containsKey(fwdTarget)) {
                    invertedMappings.add(MappingModel.direct(compName,
                            sourceParamName + "." + fwdTarget + "()"));
                    continue;
                }
            }

            // Default: same name
            if (newSourceMap.containsKey(compName)) {
                invertedMappings.add(MappingModel.direct(compName,
                        sourceParamName + "." + compName + "()"));
            } else {
                // Component not in the inverse source — was it consumed by a forward expression?
                if (!expressionMappedTargets.isEmpty()) {
                    error(method, "@InheritInverseConfiguration: cannot auto-invert component '"
                            + compName + "' in " + newTarget.getSimpleName()
                            + " — it has no matching component in " + newSource.getSimpleName()
                            + " because the forward method used expression mapping(s) for: "
                            + expressionMappedTargets
                            + ". Add @Mapping(target=\"" + compName + "\", expression=...) "
                            + "on this method to supply the inverse explicitly.");
                }
                invertedMappings.add(MappingModel.ignored(compName));
            }
        }

        return new MapperMethodModel(
                method.getSimpleName().toString(),
                sourceParamName,
                newSource.getQualifiedName().toString(),
                newTarget.getQualifiedName().toString(),
                newTarget.getSimpleName().toString(),
                invertedMappings,
                false,
                false,
                isNullSafe
        );
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private List<ExecutableElement> collectAbstractMethods(TypeElement iface) {
        return iface.getEnclosedElements().stream()
                .filter(e -> e instanceof ExecutableElement)
                .map(e -> (ExecutableElement) e)
                .filter(e -> e.getModifiers().contains(Modifier.ABSTRACT)
                        || (!e.getModifiers().contains(Modifier.DEFAULT)
                        && !e.getModifiers().contains(Modifier.STATIC)))
                .filter(e -> e.getAnnotation(BeforeMapping.class) == null)
                .filter(e -> e.getAnnotation(AfterMapping.class) == null)
                .collect(Collectors.toList());
    }

    private boolean isRecord(TypeElement element) {
        return element.getKind() == ElementKind.RECORD;
    }

    private TypeElement asTypeElement(TypeMirror mirror) {
        if (mirror == null || mirror.getKind() != TypeKind.DECLARED) return null;
        Element e = ((DeclaredType) mirror).asElement();
        return (e instanceof TypeElement te) ? te : null;
    }

    private TypeMirror unwrapOptional(TypeMirror mirror, ExecutableElement method) {
        if (mirror.getKind() != TypeKind.DECLARED) {
            error(method, "@NullSafe methods must return Optional<RecordType>");
            return null;
        }
        DeclaredType dt = (DeclaredType) mirror;
        TypeElement elem = (TypeElement) dt.asElement();
        if (!elem.getQualifiedName().toString().equals("java.util.Optional")) {
            error(method, "@NullSafe methods must return Optional<RecordType>");
            return null;
        }
        List<? extends TypeMirror> args = dt.getTypeArguments();
        if (args.size() != 1) {
            error(method, "Optional must have exactly one type argument");
            return null;
        }
        return args.get(0);
    }

    private boolean isList(TypeMirror mirror) {
        if (mirror.getKind() != TypeKind.DECLARED) return false;
        TypeElement elem = (TypeElement) ((DeclaredType) mirror).asElement();
        return elem.getQualifiedName().toString().equals("java.util.List");
    }

    private boolean isSet(TypeMirror mirror) {
        if (mirror.getKind() != TypeKind.DECLARED) return false;
        TypeElement elem = (TypeElement) ((DeclaredType) mirror).asElement();
        return elem.getQualifiedName().toString().equals("java.util.Set");
    }

    private boolean isMap(TypeMirror mirror) {
        if (mirror.getKind() != TypeKind.DECLARED) return false;
        TypeElement elem = (TypeElement) ((DeclaredType) mirror).asElement();
        return elem.getQualifiedName().toString().equals("java.util.Map");
    }

    private boolean isOptional(TypeMirror mirror) {
        if (mirror.getKind() != TypeKind.DECLARED) return false;
        TypeElement elem = (TypeElement) ((DeclaredType) mirror).asElement();
        return elem.getQualifiedName().toString().equals("java.util.Optional");
    }

    private TypeMirror typeArgument(TypeMirror mirror, int index) {
        if (mirror.getKind() != TypeKind.DECLARED) return null;
        List<? extends TypeMirror> args = ((DeclaredType) mirror).getTypeArguments();
        return (args.size() > index) ? args.get(index) : null;
    }

    private String dotChain(String paramName, String dotPath) {
        String[] parts = dotPath.split("\\.");
        StringBuilder sb = new StringBuilder(paramName);
        for (String part : parts) sb.append(".").append(part).append("()");
        return sb.toString();
    }

    private String constantExpression(String constant, TypeMirror targetType) {
        String typeName = targetType.toString();
        return switch (typeName) {
            case "java.lang.String"  -> "\"" + constant + "\"";
            case "boolean", "java.lang.Boolean" -> constant;
            case "int", "java.lang.Integer"     -> constant;
            case "long", "java.lang.Long"       -> constant + "L";
            case "double", "java.lang.Double"   -> constant + "d";
            case "float", "java.lang.Float"     -> constant + "f";
            default -> constant;
        };
    }

    private boolean matchesSourceParam(ExecutableElement method, TypeMirror sourceType) {
        return !method.getParameters().isEmpty()
                && typeUtils.isAssignable(sourceType, method.getParameters().get(0).asType());
    }

    private boolean matchesSourceAndTargetParams(ExecutableElement method, TypeMirror src, TypeMirror tgt) {
        List<? extends VariableElement> params = method.getParameters();
        return params.size() >= 2
                && typeUtils.isAssignable(src, params.get(0).asType())
                && typeUtils.isAssignable(tgt, params.get(1).asType());
    }

    private void error(Element element, String msg) {
        messager.printMessage(Diagnostic.Kind.ERROR, "[Immuto] " + msg, element);
    }

    private void warn(Element element, String msg) {
        messager.printMessage(Diagnostic.Kind.WARNING, "[Immuto] " + msg, element);
    }
}
