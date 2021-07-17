package io.github.asewhy.project.dto.optional.preprocessor.scanner;

import com.sun.source.tree.*;
import com.sun.source.util.SimpleTreeVisitor;

public class ReplaceMethodLinksAnalyzer extends SimpleTreeVisitor<String, String> {
    private final String from;
    private final String to;

    public ReplaceMethodLinksAnalyzer(String from, String to) {
        this.from = from;
        this.to = to;
    }

    @Override
    protected String defaultAction(Tree node, String s) {
        return super.defaultAction(node, s);
    }

    @Override
    public String visitIdentifier(IdentifierTree node, String trees) {
        var name = node.getName().toString();

        if(name.equals(from)) {
            return to;
        } else {
            return name;
        }
    }
}