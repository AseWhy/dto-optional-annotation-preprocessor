package io.github.asewhy.project.dto.optional.preprocessor.processors;

import io.github.asewhy.project.dto.optional.preprocessor.annotations.DateFormat;
import io.github.asewhy.project.dto.optional.preprocessor.members.FieldContainer;
import io.github.asewhy.project.dto.optional.preprocessor.processors.base.BasePreprocessor;

import javax.lang.model.element.Element;
import java.io.PrintWriter;
import java.util.List;

public class DateFormatPreprocessor extends BasePreprocessor<DateFormat> {
    public DateFormatPreprocessor(PrintWriter writer) {
        super(writer, DateFormat.class);
    }

    @Override
    protected String getSetterType() {
        return "String";
    }

    @Override
    protected String processResult() {
        return "value != null ? new SimpleDateFormat(\"" + (this.annotation.value().isEmpty() ? "yyyy-MM-dd'T'HH:mm:ssZ" : this.annotation.value()) + "\").parse(value) : null";
    }

    @Override
    protected void processBefore(FieldContainer field, Element target) {

    }

    @Override
    protected List<String> getSetterExceptions() {
        return List.of("ParseException");
    }

    @Override
    public List<String> getProvidedImports(Element field) {
        if(field.getAnnotation(this.annotation_type) != null) {
            return List.of(
                "java.text.ParseException",
                "java.text.SimpleDateFormat",
                "java.util.Date"
            );
        } else {
            return null;
        }
    }
}
