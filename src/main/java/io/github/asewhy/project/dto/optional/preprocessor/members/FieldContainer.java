package io.github.asewhy.project.dto.optional.preprocessor.members;

import javax.lang.model.element.VariableElement;

public class FieldContainer {
    public String strName;
    public String strType;
    public String strTypeAnnotations;
    public String strAccess;
    public String annotations;
    public String rootType;
    public VariableElement base;
    public Boolean hasSuperSetter;
    public Boolean hasSuperGetter;
}
