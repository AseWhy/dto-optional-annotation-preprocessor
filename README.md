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
    private String some_field;
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
	private Optional<String> some_field;

	public SomeClassRequestDTO() {
		this.some_field = null;
	}

	public Boolean hasSome_fieldField() {
		return this.some_field != null;
	}

	public String getSome_field(String def) {
		return this.some_field != null ? this.some_field.orElse(def) : def;
	}

	public String getSome_field() {
		return this.getSome_field(null);
	}

	public void setSome_field(final String value) {
		this.some_field = Optional.ofNullable(value);
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
    private String some_field;
    private String some_hidden_field;
}
```

Далее на основе этой модели можно создать DTO

```java
package ru.some;

import ResponseDTO;

@ResponseDTO({SomeModel.class})
public class SomeClassDTO {
    private String some_field;
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
	private Optional<String> some_field;

	public SomeClassResponseDTO() {
		this.some_field = null;
	}

	public SomeClassResponseDTO(SomeClass from) {
		this.setSome_field(from.getSome_field());
	}

	public Boolean hasSome_fieldField() {
		return this.some_field != null;
	}

	public String getSome_field(String def) {
		return this.some_field != null ? this.some_field.orElse(def) : def;
	}

	public String getSome_field() {
		return this.getSome_field(null);
	}

	public void setSome_field(final String value) {
		this.some_field = Optional.ofNullable(value);
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
				gen.writeStringField("some_field", value.getSome_field());
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