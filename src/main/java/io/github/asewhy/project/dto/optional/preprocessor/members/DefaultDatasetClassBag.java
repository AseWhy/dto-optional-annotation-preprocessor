package io.github.asewhy.project.dto.optional.preprocessor.members;

import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import java.util.ArrayList;
import java.util.List;

public class DefaultDatasetClassBag {
    public PackageElement pkg;
    public String new_name;
    public List<String> imports = new ArrayList<>();
    public Element base_class;
    public Element clazz;
    public List<FieldContainer> fields = new ArrayList<>();
    public List<String> constructors = new ArrayList<>();
}
