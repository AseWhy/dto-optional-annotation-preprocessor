package com.astecom.project.dto.optional.preprocessor.scanner;

import com.sun.source.tree.IdentifierTree;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CodeIdentifierUsageAnalyzer extends TreePathScanner<Object, Trees> {
    private final Map<String, String> of;
    private final ArrayList<String> useful = new ArrayList<>();

    public CodeIdentifierUsageAnalyzer(List<String> imports) {
        this.of = imports.stream().collect(Collectors.toMap(e -> Arrays.stream(e.split("\\.")).reduce((f, s) -> s).orElse(null), e -> e, (e1, e2) -> e2));
    }

    public List<String> getUseful() {
        return useful.stream().map(of::get).collect(Collectors.toList());
    }

    @Override
    public Object visitIdentifier(IdentifierTree node, Trees trees) {
        var node_name = node.getName().toString();

        if(!useful.contains(node_name) && of.containsKey(node_name)) {
            useful.add(node_name);
        }

        return super.visitIdentifier(node, trees);
    }
}