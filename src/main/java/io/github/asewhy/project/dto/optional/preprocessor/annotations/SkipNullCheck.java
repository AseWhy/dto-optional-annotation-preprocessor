package io.github.asewhy.project.dto.optional.preprocessor.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Пропустить проверку на null для этого поля.
 *
 * По умолчанию все поля, которые подлежат посредственной конвертации, это когда
 * из поля создается новый объект
 *
 * if(from.getSomething() != null) {
 *      setSomething(new SomethingDTO(from.getSomething()))
 * }
 *
 * Эта аннотация позволяет пропустить эту проверку, и превести сеттер к такому виду
 *
 * setSomething(new SomethingDTO(from.getSomething()))
 *
 * **Это может вызвать неправильное поведение в случае если поля, которым переданы значения null должны быть null**
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
public @interface SkipNullCheck {

}
