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
                error(element, "Immuto processing failed: " + e);
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

        // annotation.uses() returns Class<?>[] — calling it in APT context always throws
        // MirroredTypesException; use the exception to access the TypeMirror list instead.
        try {
            annotation.uses(); // throws in APT context
        } catch (javax.lang.model.type.MirroredTypesException e) {
            if (!e.getTypeMirrors().isEmpty()) {
                warn(iface, "@RecordMapper(uses = ...) is not yet implemented; the registered converters are ignored.");
            }
        }

        if (!annotation.named().isEmpty()) {
            warn(iface, "@RecordMapper(named = \"" + annotation.named() + "\") is not yet implemented; the qualifier is ignored.");
        }

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
        if (returnMirror.getKind() == TypeKind.VOID) {
            error(method, "Mapper method must not return void; specify a record return type");
            return null;
        }

        if (method.getAnnotation(BidirectionalMapper.class) != null) {
            warn(method, "@BidirectionalMapper is not yet implemented by the processor; "
                    + "use an explicit reverse method annotated with @InheritInverseConfiguration instead.");
        }

        TypeMirror sourceMirror = method.getParameters().get(0).asType();
        // Always use "source" in generated code so @Mapping(expression="java(source.x())") works
        // regardless of what the user named the interface method parameter.
        String sourceParamName  = "source";

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
        for (Mapping m : mappingAnnotations) {
            if (mappingByTarget.containsKey(m.target())) {
                warn(method, "Duplicate @Mapping(target=\"" + m.target() + "\") — later declaration overrides earlier.");
            }
            mappingByTarget.put(m.target(), m);
        }

        // Handle @InheritInverseConfiguration
        InheritInverseConfiguration inherit = method.getAnnotation(InheritInverseConfiguration.class);
        if (inherit != null) {
            return buildInverseMethod(method, inherit, allMethods, sourceElement,
                    targetElement, sourceParamName, isNullSafe, mappingByTarget);
        }

        // Build component mappings for target record
        List<RecordComponentElement> targetComponents = new ArrayList<>(targetElement.getRecordComponents());
        Map<String, RecordComponentElement> sourceComponentMap = new ArrayList<>(sourceElement.getRecordComponents())
                .stream().collect(Collectors.toMap(c -> c.getSimpleName().toString(), c -> c));

        // Validate that every @Mapping(target) names an actual target component
        Set<String> targetComponentNames = targetComponents.stream()
                .map(c -> c.getSimpleName().toString())
                .collect(Collectors.toSet());
        for (String mappingTarget : mappingByTarget.keySet()) {
            if (!targetComponentNames.contains(mappingTarget)) {
                error(method, "@Mapping(target=\"" + mappingTarget + "\") does not match any component "
                        + "in " + targetElement.getSimpleName() + ". Check for typos. "
                        + "Available components: " + targetComponentNames);
            }
        }

        boolean warnUnmapped = annotation.warnOnUnmappedTargetComponents();
        List<MappingModel> componentMappings = new ArrayList<>();

        for (RecordComponentElement targetComp : targetComponents) {
            String compName = targetComp.getSimpleName().toString();
            MappingModel mappingModel = resolveComponentMapping(
                    compName, targetComp, sourceComponentMap, mappingByTarget,
                    sourceParamName, method, warnUnmapped);
            if (mappingModel != null) componentMappings.add(mappingModel);
        }

        String beforeName = elementUtils.getAllMembers(iface).stream()
                .filter(e -> e instanceof ExecutableElement ee
                        && ee.getAnnotation(BeforeMapping.class) != null
                        && matchesSourceParam(ee, sourceMirror))
                .map(e -> {
                    ExecutableElement ee = (ExecutableElement) e;
                    String name = ee.getSimpleName().toString();
                    if (ee.getModifiers().contains(Modifier.STATIC)) {
                        TypeElement declaring = (TypeElement) ee.getEnclosingElement();
                        return declaring.getQualifiedName() + "." + name;
                    }
                    return name;
                })
                .findFirst().orElse(null);

        String afterName = elementUtils.getAllMembers(iface).stream()
                .filter(e -> e instanceof ExecutableElement ee
                        && ee.getAnnotation(AfterMapping.class) != null
                        && matchesSourceAndTargetParams(ee, sourceMirror, targetMirror))
                .map(e -> {
                    ExecutableElement ee = (ExecutableElement) e;
                    String name = ee.getSimpleName().toString();
                    if (ee.getModifiers().contains(Modifier.STATIC)) {
                        TypeElement declaring = (TypeElement) ee.getEnclosingElement();
                        return declaring.getQualifiedName() + "." + name;
                    }
                    return name;
                })
                .findFirst().orElse(null);

        return new MapperMethodModel(
                method.getSimpleName().toString(),
                sourceParamName,
                sourceElement.getQualifiedName().toString(),
                targetElement.getQualifiedName().toString(),
                targetElement.getSimpleName().toString(),
                componentMappings,
                beforeName,
                afterName,
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
            if (!override.qualifiedBy().isEmpty()) {
                warn(method, "@Mapping(target=\"" + compName + "\", qualifiedBy=...) is not yet implemented; the qualifier is ignored.");
            }
            if (override.defaultForNull()) {
                warn(method, "@Mapping(target=\"" + compName + "\", defaultForNull=true) is not yet implemented; the flag is ignored.");
            }
            if (!override.defaultExpression().isEmpty()) {
                warn(method, "@Mapping(target=\"" + compName + "\", defaultExpression=...) is not yet implemented; the expression is ignored.");
            }

            int setCount = (override.ignore() ? 1 : 0)
                    + (!override.expression().isEmpty() ? 1 : 0)
                    + (!override.constant().isEmpty() ? 1 : 0)
                    + (!override.source().isEmpty() ? 1 : 0);
            if (setCount == 0) {
                warn(method, "@Mapping(target=\"" + compName + "\") has no effect — no source, expression, "
                        + "constant, or ignore attribute is set. Remove this annotation.");
            } else if (setCount > 1) {
                warn(method, "@Mapping(target=\"" + compName + "\") has multiple mutually-exclusive attributes set "
                        + "(precedence: ignore > expression > constant > source).");
            }

            if (override.ignore()) {
                return MappingModel.direct(compName, primitiveDefault(targetComp.asType()));
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
                String srcPath = override.source();
                for (String part : srcPath.split("\\.", -1)) {
                    if (part.isEmpty()) {
                        error(method, "@Mapping(target=\"" + compName + "\", source=\"" + srcPath + "\") "
                                + "contains an empty path segment (check for consecutive or leading/trailing dots).");
                        return MappingModel.direct(compName, primitiveDefault(targetComp.asType()));
                    }
                }
                return MappingModel.direct(compName, dotChain(sourceParam, srcPath));
            }
        }

        // default: same-name lookup
        RecordComponentElement sourceComp = sourceMap.get(compName);
        if (sourceComp != null) {
            return autoMap(compName, sourceComp.asType(), targetComp.asType(), sourceParam, method);
        }

        // Not found in source
        if (warnUnmapped) {
            warn(method, "Target component '" + compName + "' has no matching source component.");
        } else {
            error(method, "Target component '" + compName + "' has no matching source component. "
                    + "Use @Mapping(target=\"" + compName + "\", ignore=true) to suppress this error.");
        }
        return MappingModel.direct(compName, primitiveDefault(targetComp.asType()));
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
            boolean isNullSafe,
            Map<String, Mapping> ownMappings) {

        String forwardName = inherit.name();
        Optional<ExecutableElement> forwardMethod = allMethods.stream()
                .filter(m -> !m.equals(method))
                .filter(m -> {
                    if (!forwardName.isEmpty()) return m.getSimpleName().toString().equals(forwardName);
                    // auto-find: return type of forward == our source, param of forward == our target
                    if (m.getParameters().size() != 1) return false;
                    TypeMirror fwdReturn = m.getReturnType();
                    TypeMirror fwdParam  = m.getParameters().get(0).asType();
                    // Unwrap Optional for @NullSafe forward methods
                    if (isOptional(fwdReturn)) {
                        TypeMirror unwrapped = typeArgument(fwdReturn, 0);
                        if (unwrapped != null) fwdReturn = unwrapped;
                    }
                    return typeUtils.isSameType(fwdReturn, sourceElement.asType())
                            && typeUtils.isSameType(fwdParam, targetElement.asType());
                })
                .findFirst();

        if (forwardMethod.isEmpty()) {
            error(method, "@InheritInverseConfiguration: cannot find the forward mapping method.");
            return null;
        }

        ExecutableElement fwd = forwardMethod.get();
        Mapping[] fwdMappings = fwd.getAnnotationsByType(Mapping.class);

        // invert: source/target swap for each Mapping that used explicit source/target names
        // Use the already-resolved elements (handles @NullSafe Optional<T> unwrapping correctly)
        List<MappingModel> invertedMappings = new ArrayList<>();
        TypeElement newSource = sourceElement;
        TypeElement newTarget = targetElement;

        if (newSource == null || newTarget == null) {
            error(method, "@InheritInverseConfiguration: cannot resolve inverted source/target types");
            return null;
        }

        Map<String, RecordComponentElement> newSourceMap = new ArrayList<>(newSource.getRecordComponents())
                .stream().collect(Collectors.toMap(c -> c.getSimpleName().toString(), c -> c));

        // Validate that own @Mapping targets on this inverse method name real target components
        Set<String> targetComponentNames = newTarget.getRecordComponents().stream()
                .map(c -> c.getSimpleName().toString())
                .collect(Collectors.toSet());
        for (String ownTarget : ownMappings.keySet()) {
            if (!targetComponentNames.contains(ownTarget)) {
                error(method, "@Mapping(target=\"" + ownTarget + "\") does not match any component "
                        + "in " + newTarget.getSimpleName() + ". Available: " + targetComponentNames);
            }
        }

        // Expression-mapped forward targets cannot be automatically inverted
        Set<String> expressionMappedTargets = Arrays.stream(fwdMappings)
                .filter(m -> !m.expression().isEmpty())
                .map(Mapping::target)
                .collect(Collectors.toSet());

        for (RecordComponentElement targetComp : new ArrayList<>(newTarget.getRecordComponents())) {
            String compName = targetComp.getSimpleName().toString();

            // 1. Explicit @Mapping override on this inverse method takes highest priority
            Mapping ownOverride = ownMappings.get(compName);
            if (ownOverride != null) {
                if (!ownOverride.qualifiedBy().isEmpty()) {
                    warn(method, "@Mapping(target=\"" + compName + "\", qualifiedBy=...) is not yet implemented; the qualifier is ignored.");
                }
                if (ownOverride.defaultForNull()) {
                    warn(method, "@Mapping(target=\"" + compName + "\", defaultForNull=true) is not yet implemented; the flag is ignored.");
                }
                if (!ownOverride.defaultExpression().isEmpty()) {
                    warn(method, "@Mapping(target=\"" + compName + "\", defaultExpression=...) is not yet implemented; the expression is ignored.");
                }
                if (ownOverride.ignore()) {
                    invertedMappings.add(MappingModel.direct(compName, primitiveDefault(targetComp.asType())));
                    continue;
                }
                if (!ownOverride.expression().isEmpty()) {
                    String expr = ownOverride.expression().trim();
                    if (expr.startsWith("java(") && expr.endsWith(")")) {
                        expr = expr.substring(5, expr.length() - 1);
                    }
                    invertedMappings.add(MappingModel.direct(compName, expr));
                    continue;
                }
                if (!ownOverride.constant().isEmpty()) {
                    invertedMappings.add(MappingModel.direct(compName,
                            constantExpression(ownOverride.constant(), targetComp.asType())));
                    continue;
                }
                if (!ownOverride.source().isEmpty()) {
                    String srcPath = ownOverride.source();
                    boolean pathValid = true;
                    for (String part : srcPath.split("\\.", -1)) {
                        if (part.isEmpty()) {
                            error(method, "@Mapping(target=\"" + compName + "\", source=\"" + srcPath + "\") "
                                    + "contains an empty path segment.");
                            invertedMappings.add(MappingModel.direct(compName, primitiveDefault(targetComp.asType())));
                            pathValid = false;
                            break;
                        }
                    }
                    if (pathValid) {
                        invertedMappings.add(MappingModel.direct(compName, dotChain(sourceParamName, srcPath)));
                    }
                    continue;
                }
                // @Mapping with no actionable attribute — warn and fall through to auto-invert
                warn(method, "@Mapping(target=\"" + compName + "\") has no effect — no source, expression, "
                        + "constant, or ignore attribute is set. Remove this annotation.");
            }

            // 2. Find a forward mapping that had this as its source → now it's our target
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

            // 3. Default: same name — use the shared type-aware helper to handle
            // record→record and List/Set/Map<Record> inversions correctly
            if (newSourceMap.containsKey(compName)) {
                RecordComponentElement srcComp = newSourceMap.get(compName);
                invertedMappings.add(autoMap(compName, srcComp.asType(), targetComp.asType(),
                        sourceParamName, method));
            } else {
                // 4. Cannot auto-invert — error
                String hint = expressionMappedTargets.isEmpty()
                        ? "it has no matching component in " + newSource.getSimpleName() + "."
                        : "the forward method used expression mapping(s) for: " + expressionMappedTargets + ".";
                error(method, "@InheritInverseConfiguration: cannot auto-invert component '"
                        + compName + "' in " + newTarget.getSimpleName() + " — " + hint
                        + " Add @Mapping(target=\"" + compName + "\", expression=...) "
                        + "on this method to supply the inverse explicitly, "
                        + "or @Mapping(target=\"" + compName + "\", ignore=true) to suppress.");
                invertedMappings.add(MappingModel.direct(compName, primitiveDefault(targetComp.asType())));
            }
        }

        return new MapperMethodModel(
                method.getSimpleName().toString(),
                sourceParamName,
                newSource.getQualifiedName().toString(),
                newTarget.getQualifiedName().toString(),
                newTarget.getSimpleName().toString(),
                invertedMappings,
                null,
                null,
                isNullSafe
        );
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Generates a {@link MappingModel} for component {@code compName} by auto-detecting
     * the relationship between {@code srcType} and {@code tgtType}:
     * directly assignable, record→record (direct constructor call), List/Set/Map of records, or Optional.
     * Emits a compile error and returns an ignored mapping if no strategy applies.
     * All generated code is reflection-free.
     */
    private MappingModel autoMap(String compName, TypeMirror srcType, TypeMirror tgtType,
            String sourceParam, ExecutableElement method) {

        if (typeUtils.isAssignable(srcType, tgtType)) {
            return MappingModel.direct(compName, sourceParam + "." + compName + "()");
        }

        TypeElement srcElem = asTypeElement(srcType);
        TypeElement tgtElem = asTypeElement(tgtType);

        if (srcElem != null && isRecord(srcElem) && tgtElem != null && isRecord(tgtElem)) {
            String nestedSrc = sourceParam + "." + compName + "()";
            return MappingModel.direct(compName,
                    nestedSrc + " == null ? null : "
                            + generateConstructorCall(nestedSrc, srcElem, tgtElem, method));
        }

        if (isList(srcType) && isList(tgtType)) {
            TypeElement srcEl = asTypeElement(typeArgument(srcType, 0));
            TypeElement tgtEl = asTypeElement(typeArgument(tgtType, 0));
            if (srcEl != null && isRecord(srcEl) && tgtEl != null && isRecord(tgtEl)) {
                String listExpr = sourceParam + "." + compName + "()";
                String elemCtor = generateConstructorCall("e", srcEl, tgtEl, method);
                return MappingModel.direct(compName,
                        listExpr + " == null ? null : "
                                + listExpr + ".stream()"
                                + ".map(e -> e == null ? null : " + elemCtor + ")"
                                + ".collect(java.util.stream.Collectors.toUnmodifiableList())");
            }
        }

        if (isSet(srcType) && isSet(tgtType)) {
            TypeElement srcEl = asTypeElement(typeArgument(srcType, 0));
            TypeElement tgtEl = asTypeElement(typeArgument(tgtType, 0));
            if (srcEl != null && isRecord(srcEl) && tgtEl != null && isRecord(tgtEl)) {
                String setExpr = sourceParam + "." + compName + "()";
                String elemCtor = generateConstructorCall("e", srcEl, tgtEl, method);
                return MappingModel.direct(compName,
                        setExpr + " == null ? null : "
                                + setExpr + ".stream()"
                                + ".map(e -> e == null ? null : " + elemCtor + ")"
                                + ".collect(java.util.stream.Collectors.toUnmodifiableSet())");
            }
        }

        if (isMap(srcType) && isMap(tgtType)) {
            TypeMirror srcKey = typeArgument(srcType, 0);
            TypeMirror tgtKey = typeArgument(tgtType, 0);
            TypeElement srcVal = asTypeElement(typeArgument(srcType, 1));
            TypeElement tgtVal = asTypeElement(typeArgument(tgtType, 1));
            if (srcKey != null && tgtKey != null && typeUtils.isAssignable(srcKey, tgtKey)
                    && srcVal != null && isRecord(srcVal) && tgtVal != null && isRecord(tgtVal)) {
                String mapExpr = sourceParam + "." + compName + "()";
                String valCtor = generateConstructorCall("e.getValue()", srcVal, tgtVal, method);
                return MappingModel.direct(compName,
                        mapExpr + " == null ? null : "
                                + mapExpr + ".entrySet().stream()"
                                + ".collect(java.util.stream.Collectors.toUnmodifiableMap("
                                + "java.util.Map.Entry::getKey, "
                                + "e -> e.getValue() == null ? null : " + valCtor + "))");
            }
        }

        error(method, "Cannot auto-convert component '" + compName + "': "
                + srcType + " → " + tgtType
                + ". Provide an explicit @Mapping or a TypeConverter.");
        return MappingModel.direct(compName, primitiveDefault(tgtType));
    }

    /**
     * Generates a direct, reflection-free constructor call expression for a record-to-record
     * mapping: {@code new TargetType(srcExpr.comp1(), srcExpr.comp2(), ...)}.
     * Components present in the target but absent in the source receive the zero-value for
     * primitive types or {@code null} for reference types.
     */
    private String generateConstructorCall(String srcExpr, TypeElement srcElem, TypeElement tgtElem,
            ExecutableElement method) {
        Map<String, RecordComponentElement> srcCompMap = new ArrayList<>(srcElem.getRecordComponents())
                .stream().collect(Collectors.toMap(c -> c.getSimpleName().toString(), c -> c));

        List<String> args = new ArrayList<>();
        for (RecordComponentElement tgtComp : tgtElem.getRecordComponents()) {
            String name = tgtComp.getSimpleName().toString();
            RecordComponentElement srcComp = srcCompMap.get(name);
            if (srcComp != null) {
                MappingModel nested = autoMap(name, srcComp.asType(), tgtComp.asType(), srcExpr, method);
                args.add(nested.sourceExpression());
            } else {
                args.add(primitiveDefault(tgtComp.asType()));
            }
        }

        return "new " + tgtElem.getQualifiedName() + "(" + String.join(", ", args) + ")";
    }

    private List<ExecutableElement> collectAbstractMethods(TypeElement iface) {
        List<ExecutableElement> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        collectAbstractMethodsRecursive(iface, result, seen);
        return result;
    }

    private void collectAbstractMethodsRecursive(TypeElement type, List<ExecutableElement> result, Set<String> seen) {
        for (Element enclosed : type.getEnclosedElements()) {
            if (!(enclosed instanceof ExecutableElement ee)) continue;
            if (ee.getModifiers().contains(Modifier.PRIVATE)) continue;
            if (ee.getModifiers().contains(Modifier.DEFAULT)) continue;
            if (ee.getModifiers().contains(Modifier.STATIC)) continue;
            if (ee.getAnnotation(BeforeMapping.class) != null) continue;
            if (ee.getAnnotation(AfterMapping.class) != null) continue;
            String sig = ee.getSimpleName().toString() + typeUtils.erasure(ee.asType()).toString();
            if (seen.add(sig)) result.add(ee);
        }
        for (TypeMirror parent : type.getInterfaces()) {
            TypeElement parentElem = asTypeElement(parent);
            if (parentElem != null) collectAbstractMethodsRecursive(parentElem, result, seen);
        }
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
        StringBuilder result = new StringBuilder();
        StringBuilder chain = new StringBuilder(paramName);
        for (int i = 0; i < parts.length - 1; i++) {
            chain.append(".").append(parts[i]).append("()");
            result.append(chain).append(" == null ? null : ");
        }
        chain.append(".").append(parts[parts.length - 1]).append("()");
        result.append(chain);
        return result.toString();
    }

    private String constantExpression(String constant, TypeMirror targetType) {
        String typeName = targetType.toString();
        String escaped = constant.replace("\\", "\\\\").replace("\"", "\\\"");
        return switch (typeName) {
            case "java.lang.String"              -> "\"" + escaped + "\"";
            case "boolean", "java.lang.Boolean"  -> constant;
            case "int", "java.lang.Integer"      -> constant;
            case "long", "java.lang.Long"        -> constant + "L";
            case "double", "java.lang.Double"    -> constant + "d";
            case "float", "java.lang.Float"      -> constant + "f";
            case "byte", "java.lang.Byte"        -> "(byte) " + constant;
            case "short", "java.lang.Short"      -> "(short) " + constant;
            case "char", "java.lang.Character" -> {
                String charEscaped = constant.replace("\\", "\\\\").replace("'", "\\'");
                yield "'" + charEscaped + "'";
            }
            case "java.math.BigDecimal"          -> "new java.math.BigDecimal(\"" + escaped + "\")";
            case "java.math.BigInteger"          -> "new java.math.BigInteger(\"" + escaped + "\")";
            default -> {
                TypeElement typeElement = asTypeElement(targetType);
                if (typeElement != null && typeElement.getKind() == ElementKind.ENUM) {
                    yield typeName + "." + constant;
                }
                yield constant;
            }
        };
    }

    private String primitiveDefault(TypeMirror type) {
        return switch (type.getKind()) {
            case BOOLEAN          -> "false";
            case BYTE, SHORT, INT, CHAR -> "0";
            case LONG             -> "0L";
            case FLOAT            -> "0.0f";
            case DOUBLE           -> "0.0d";
            default               -> "null";
        };
    }

    private boolean matchesSourceParam(ExecutableElement method, TypeMirror sourceType) {
        List<? extends VariableElement> params = method.getParameters();
        return params.size() == 1
                && typeUtils.isAssignable(sourceType, params.get(0).asType());
    }

    private boolean matchesSourceAndTargetParams(ExecutableElement method, TypeMirror src, TypeMirror tgt) {
        List<? extends VariableElement> params = method.getParameters();
        return params.size() == 2
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
