package io.github.asewhy.project.dto.optional.preprocessor.members;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GenericBag {
    public String simpleRoot;
    public String fullRoot;
    public ArrayList<String> provided_imports = new ArrayList<>();
    public ArrayList<GenericBag> generics = new ArrayList<>();
    public ArrayList<Annotation> annotations = new ArrayList<>();

    public ArrayList<String> getImports() {
        var imports = new ArrayList<>(this.provided_imports);

        for(var generic: this.generics) {
            imports.addAll(generic.getImports());
        }

        for(var annotation: this.annotations) {
            imports.addAll(annotation.provided_imports);
        }

        return imports;
    }

    public String getAnnotations() {
        return this.annotations.size() > 0 ? this.annotations.stream().map(Annotation::getRoot).collect(Collectors.joining(" ")) + " " : "";
    }

    public String getAnnotations(List<Annotation> concat) {
        var annotation = new ArrayList<>(this.annotations);
        annotation.addAll(concat);
        return annotation.size() > 0 ? annotation.stream().map(Annotation::getRoot).collect(Collectors.joining(" ")) + " " : "";
    }

    public String getRoot() {
        StringBuilder name = new StringBuilder(this.simpleRoot);

        if (this.generics.size() > 0) {
            name.append("<").append(this.generics.stream().map(GenericBag::getRoot).collect(Collectors.joining(", "))).append(">");
        }

        return name.toString();
    }
}
