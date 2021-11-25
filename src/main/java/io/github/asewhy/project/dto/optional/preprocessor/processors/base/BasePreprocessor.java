package io.github.asewhy.project.dto.optional.preprocessor.processors.base;

import io.github.asewhy.project.dto.optional.preprocessor.members.FieldContainer;
import io.github.asewhy.project.dto.optional.preprocessor.members.SettingsBag;
import io.github.asewhy.project.dto.optional.preprocessor.utils.APUtils;

import javax.lang.model.element.Element;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.util.List;

public abstract class BasePreprocessor<A extends Annotation> {
    protected final PrintWriter writer;
    protected final Class<A> annotation_type;
    protected A annotation;

    protected BasePreprocessor(PrintWriter writer, Class<A> annotation_type) {
        this.writer = writer;
        this.annotation_type = annotation_type;
    }

    protected abstract void processBefore(FieldContainer field, Element target);
    protected abstract String processResult();
    protected abstract List<String> getSetterExceptions();

    public Boolean process(
        Element field_element,
        FieldContainer field,
        Boolean serializer_enabled,
        SettingsBag settings
    ) {
        annotation = field_element.getAnnotation(annotation_type);

        if(annotation != null) {
            var a_throws = getSetterExceptions();
            var result = processResult();

            writer.println("\t@JsonProperty(\"" + APUtils.convertToCurrentCase(field.str_name, settings.policy) + "\")");
            writer.print("\tpublic void ");
            writer.print(APUtils.toSetter(field.str_name));
            writer.print("(");
            writer.print(this.getSetterType() != null ? this.getSetterType() : field.str_type);
            writer.print(" value) ");

            if(a_throws != null && a_throws.size() > 0) {
                writer.print("throws ");
                writer.print(String.join(", ", a_throws));
                writer.println(" {");
            } else {
                writer.println("{");
            }

            processBefore(field, field_element);

            if(serializer_enabled) {
                writer.print("\t\tthis.");
                writer.print(field.str_name);
                writer.print(" = Optional.ofNullable(");
                writer.print(result);
                writer.println(");");
            } else {
                writer.print("\t\tthis.");
                writer.print(field.str_name);
                writer.print(" = ");
                writer.print(result);
                writer.println(";");
            }

            writer.println("\t}");

            return true;
        }

        return false;
    }

    public abstract List<String> getProvidedImports(Element field);
    public abstract Boolean isTargetAnnotated(Element field);
    public abstract String getSetterType();
}
