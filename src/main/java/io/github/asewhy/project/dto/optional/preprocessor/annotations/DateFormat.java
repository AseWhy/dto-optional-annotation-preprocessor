package io.github.asewhy.project.dto.optional.preprocessor.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Эта аннотация позволяет указать формат даты которая будет предоставлена запросом. Эта аннотация будет разобрана только
 * RequestDTO препроцессором, ResponseDTO препроцессор будет игнорировать эту аннотацию.
 *
 * Пример использования:
 *
 * ```java
 * @DateFormat("dd.MM.yyyy, hh:mm")
 * private Date created_at;
 * ```
 *
 * В этом случае дата которая будет предоставлена запросом будет разобрана в формате dd.MM.yyyy, hh:mm
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
public @interface DateFormat {
    String value();
}
