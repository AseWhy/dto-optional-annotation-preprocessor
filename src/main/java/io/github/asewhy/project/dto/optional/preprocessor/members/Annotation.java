package io.github.asewhy.project.dto.optional.preprocessor.members;

import java.util.ArrayList;
import java.util.HashMap;

public class Annotation {
    public String name;
    public ArrayList<String> provided_imports = new ArrayList<>();
    public HashMap<String, String> params = new HashMap<>();

    public String getRoot() {
        StringBuilder name = new StringBuilder("@" + this.name);

        if (params.size() > 0) {
            name.append("(");

            for (var entry : params.entrySet()) {
                name.append(entry.getKey()).append(" = ").append(entry.getValue()).append(", ");
            }

            name.delete(name.length() - 2, name.length());

            name.append(")");
        }

        return name.toString();
    }
}
