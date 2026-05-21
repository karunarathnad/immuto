package io.github.karunarathnad.immuto.annotation;

import java.lang.annotation.*;

/**
 * Container annotation for repeatable {@link Mapping} declarations.
 * Java handles this automatically — you do not need to use it directly.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
@Documented
public @interface Mappings {
    Mapping[] value();
}
