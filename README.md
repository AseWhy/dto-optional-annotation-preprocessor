# dto-optional-annotation-preprocessor

Преобразовать ДТО'шек для spring boot запросов и ответов.

## Зачем?
Впринципе можно использовать lombok в связке с Optional, для достижения
такой же функциональности, а можно просто пометить класс как `@RequestDTO`
и по этому классу будет сгенерирован новый класс с измененным названием.
Связка с `Optional` позволяет использовать опциональные параметры при
обработке запроса. Тогда как при использовании не обернутых полей будет
невозможно отличить поле, которое не было передано, от null. При
использовании RequestDTO такая марока отпадает т.к. появляется метод
has`field_name`Field которые вернет true в случае если поле было передано
в запросе

## Как пользоваться?
### Установка
```xml
<dependency>
    <groupId>io.github.asewhy</groupId>
    <artifactId>dto-optional-annotation-preprocessor</artifactId>
    <version>0.0.2</version>
</dependency>
```

### Запросы
Пример создания DTO запроса

```java
package ru.some;

import RequestDTO;

@RequestDTO
class SomeClassDTO {
    // 
    // При конверсии в объект запроса это поле будет преобразовано в snake_case -> some_field как поле json, я так сделал т.к. большинство
    // моих проектов используют имено этот стиль именования полей при передаче JSON. А т.к. в java есть соглашение о camelCase именах, то
    // тут они будут использоваться как camelCase.
    //
    // Проще говоря, в теле запроса будет some_field тут это будет преобразовано в someField.
    // 
    private String someField;
}
```

После сборки на выходе будет следующий класс:

```java
package ru.some;

import java.util.Optional;

/**
 * Сгенерировано автоматически с помощью dto-optional-annotation-preprocessor
 * Этот класс нельзя использовать как ответ сервера, из-за того что Optional не дружит с маппером, т.к. не реализует serializable
 * Для ответа сервера следует отметить целевой класс аннотацией @ResponseDTO и использовать TargetClassName + ResponseDTO
 * Это реализация Data Transfer Object для запроса. Реализованно от класса @see {@link SomeClassDTO}
 */
public class SomeClassRequestDTO {
	private Optional<String> someField;

	public SomeClassRequestDTO() {
		this.someField = null;
	}

	public Boolean hasSomeFieldField() {
		return this.someField != null;
	}

	public String getSomeFiled(String def) {
		return this.someField != null ? this.someField.orElse(def) : def;
	}

	public String getSomeFiled() {
		return this.getSomeFiled(null);
	}

	public void setSomeField(final String value) {
		this.someField = Optional.ofNullable(value);
	}
}
```

### Ответы
Пример создания DTO ответа

Сначала я создам какой-нибудь класс из которого мне может
потребоваться преобразования в DTO ответа, у вас это может быть модель
или другая DTO:

```java
package ru.some;

import lombok.Data;

@Data
public class SomeClass {
    private String someField;
    private String someHiddenField;
}
```

Далее на основе этой модели можно создать DTO

```java
package ru.some;

import ResponseDTO;

@ResponseDTO({SomeModel.class})
public class SomeClassDTO {
    // 
    // Точно такая-же ситуация и с dto ответа, названия будут принудительно преобразованы в snake_case, в то время как в
    // геттеры сеттеры и функции проверки наличия останутся с camelCase оригинальным названием
    //
    // При конверсии из связанных сущностей так-же будут использоваться поля с оригинальными названиями.
    // 
    // Проще говоря, тут someField а в ответе будет поле some_field.
    // 
    private String someField;
}
```

После сборки на выходе будет следующий класс:

```java
package ru.some;

import java.util.Optional;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Сгенерировано автоматически с помощью dto-optional-annotation-preprocessor
 * Этот класс можно использовать как ответ сервера, тут предусмотрен свой сериализатор
 * Это реализация Data Transfer Object для ответа. Реализованно от класса @see {@link SomeClassDTO}
 */
@JsonSerialize(using = SomeClassDTOResponseSerializer.class)
public class SomeClassResponseDTO {
	private Optional<String> someField;

	public SomeClassResponseDTO() {
		this.someField = null;
	}

	public SomeClassResponseDTO(SomeClass from) {
		this.setSomeField(from.getSomeField());
	}

	public Boolean hasSomeFieldField() {
		return this.someField != null;
	}

	public String getSomeField(String def) {
		return this.someField != null ? this.someField.orElse(def) : def;
	}

	public String getSomeField() {
		return this.getSomeField(null);
	}

	public void setSomeField(final String value) {
		this.someField = Optional.ofNullable(value);
	}
}
```

Так-же к этому классу в этом-же пакете будет создан package scope класс сериализатора:

```java
package ru.some;

import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;

/**
 * Сгенерировано автоматически с помощью dto-optional-annotation-preprocessor
 * Этот класс используется для сериализации объекта @see {@link SomeClassResponseDTO}.
 * Реализованно от класса @see {@link SomeClassDTO}
 */
class SomeClassDTOResponseSerializer extends StdSerializer<SomeClassResponseDTO> {
	public SomeClassDTOResponseSerializer() {
		super((Class) null);
	}

	public SomeClassDTOResponseSerializer(Class<SomeClassResponseDTO> from) {
		super(from);
	}

	@Override
	public void serialize(SomeClassResponseDTO value, JsonGenerator gen, SerializerProvider provider) throws IOException {
		gen.writeStartObject();

		if(value.hasSome_fieldField()) {
			if(value.getSome_field() != null) {
				gen.writeStringField("some_field", value.getSomeField());
			} else {
				gen.writeNullField("some_field");
			}
		}

		gen.writeEndObject();
	}
}
```

Так-же в процессе создания конвертера могут возникнуть ошибки, в случе ошибки поле не будет создано, и в
выводе появится следующее сообщения:

```text
When creating the converter SomeClassResponseDTO -> SomeClass 1 fields were omitted
```

В целом оно значит что при создании DTO ответа SomeClassResponseDTO произошли какие-то ошибки,
и некоторые поля не могут быть конвертированы автоматически. Далее указанно количество этих полей.

Кстати если какие-то поля никак не могут быть конвертированы автоматически, вы можете написать свой конструктор
с преобразованием нужного поля, его содержимое(с учетом импортов) будет помещено в основной конвертер.

При создании DTO, который состоит из других DTO, можно указать только исходный DTO объект, название будет выбрано в соответствии с
типом DTO - если исходный объект аннотирован `@RequestDTO` и тип поля этого объекта `@RequestDTO` то тип этого поля будет именован в соответствии
с именованием всех `@RequestDTO` аннотаций.