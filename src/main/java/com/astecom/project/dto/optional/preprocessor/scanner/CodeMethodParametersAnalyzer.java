package com.astecom.project.dto.optional.preprocessor.scanner;

import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreeScanner;

public class CodeMethodParametersAnalyzer extends TreeScanner<Object, Void> {
    private String name = null;

    public String getName() {
        return name;
    }

    @Override
    public Object visitVariable(VariableTree node, Void unused) {
        return name = node.getName().toString();
    }
}