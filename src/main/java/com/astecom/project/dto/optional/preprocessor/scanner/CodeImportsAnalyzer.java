package com.astecom.project.dto.optional.preprocessor.scanner;

import com.sun.source.tree.ImportTree;
import com.sun.source.util.TreeScanner;

import java.util.ArrayList;

public class CodeImportsAnalyzer extends TreeScanner<Object, Void> {
    private final ArrayList<String> imports = new ArrayList<>();

    public ArrayList<String> getImports() {
        return imports;
    }

    @Override
    public Object visitImport(ImportTree node, Void p) {
        imports.add(node.getQualifiedIdentifier().toString());

        return super.visitImport(node, p);
    }
}