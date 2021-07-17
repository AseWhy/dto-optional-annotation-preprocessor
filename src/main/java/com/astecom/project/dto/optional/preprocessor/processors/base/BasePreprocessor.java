package com.astecom.project.dto.optional.preprocessor.processors.base;

import com.astecom.project.dto.optional.preprocessor.members.FieldContainer;
import com.astecom.project.dto.optional.preprocessor.utils.APUtils;

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
    protected abstract String getSetterType();
    protected abstract String processResult();
    protected abstract List<String> getSetterExceptions();

    public Boolean process(
        Element field_element,
        FieldContainer field,
        Boolean serializer_enabled
    ) {
        annotation = field_element.getAnnotation(annotation_type);

        if(annotation != null) {
            var a_throws = getSetterExceptions();
            var result = processResult();

            writer.print("\tpublic void " + APUtils.toSnakeSetter(field.str_name) + "(" + (this.getSetterType() != null ? this.getSetterType() : field.str_type) + " value) ");

            if(a_throws != null && a_throws.size() > 0) {
                writer.println("throws " + String.join(", ", a_throws) + " {");
            } else {
                writer.println("{");
            }

            processBefore(field, field_element);

            if(serializer_enabled) {
                writer.println("\t\tthis." + field.str_name + " = Optional.ofNullable(" + result + ");");
            } else {
                writer.println("\t\tthis." + field.str_name + " = " + result + ";");
            }

            writer.println("\t}");

            return true;
        }

        return false;
    }

    public abstract List<String> getProvidedImports(Element field);
}
