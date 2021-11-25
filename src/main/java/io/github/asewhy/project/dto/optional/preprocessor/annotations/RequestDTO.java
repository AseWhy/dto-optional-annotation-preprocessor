package io.github.asewhy.project.dto.optional.preprocessor.annotations;

import io.github.asewhy.project.dto.optional.preprocessor.enums.FieldPolicy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface RequestDTO {
    FieldPolicy policy() default FieldPolicy.SnakeCase;

    boolean createBag() default false;
}
