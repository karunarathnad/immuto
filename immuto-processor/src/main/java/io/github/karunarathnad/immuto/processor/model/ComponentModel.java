package io.github.karunarathnad.immuto.processor.model;

import javax.lang.model.type.TypeMirror;

/**
 * Immutable descriptor of one record component as seen by the annotation processor.
 *
 * @param name      component name (matches the accessor method name)
 * @param type      component type mirror
 * @param index     position in the canonical constructor
 */
public record ComponentModel(
        String name,
        TypeMirror type,
        int index
) {}
