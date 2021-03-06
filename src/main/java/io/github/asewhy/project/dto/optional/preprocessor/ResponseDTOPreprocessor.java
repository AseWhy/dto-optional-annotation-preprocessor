package io.github.asewhy.project.dto.optional.preprocessor;

import io.github.asewhy.project.dto.optional.preprocessor.annotations.RequestDTO;
import io.github.asewhy.project.dto.optional.preprocessor.annotations.ResponseDTO;
import io.github.asewhy.project.dto.optional.preprocessor.members.*;
import io.github.asewhy.project.dto.optional.preprocessor.scanner.CodeIdentifierUsageAnalyzer;
import io.github.asewhy.project.dto.optional.preprocessor.scanner.CodeImportsAnalyzer;
import io.github.asewhy.project.dto.optional.preprocessor.scanner.CodeMethodParametersAnalyzer;
import io.github.asewhy.project.dto.optional.preprocessor.annotations.SkipNullCheck;
import io.github.asewhy.project.dto.optional.preprocessor.utils.APUtils;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.Trees;
import io.github.asewhy.project.dto.optional.preprocessor.utils.CallbackMatcher;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

@SupportedAnnotationTypes({ "io.github.asewhy.project.dto.optional.preprocessor.annotations.ResponseDTO" })
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public class ResponseDTOPreprocessor extends AbstractProcessor {
    protected Trees trees;
    protected Types typeUtils;
    protected Elements elementUtils;
    protected Filer filter;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        var env = APUtils.unwrap(processingEnv);
        assert env != null;
        this.trees = Trees.instance(env);
        this.typeUtils = env.getTypeUtils();
        this.elementUtils = env.getElementUtils();
        this.filter = env.getFiler();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (var clazz : roundEnv.getElementsAnnotatedWith(ResponseDTO.class)) {
            if(clazz.getKind() != ElementKind.CLASS) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "The @ResponseDTO annotation is only allowed as class annotation");
                break;
            }

            var pkg = elementUtils.getPackageOf(clazz);
            var annotation = clazz.getAnnotation(ResponseDTO.class);
            var serializer_enabled = APUtils.classesExists("com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.databind.SerializerProvider", "com.fasterxml.jackson.databind.ser.std.StdSerializer", "com.fasterxml.jackson.databind.annotation.JsonSerialize") && annotation.serializer();

            try {
                if (serializer_enabled) {
                    makeDefaultSerializerFrom(clazz, pkg, annotation);
                }

                makeDefaultResponseFrom(clazz, serializer_enabled, annotation, pkg);
            } catch (Exception e) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.toString());
            }
        }

        return true;
    }

    private void makeDefaultSerializerFrom(Element clazz, PackageElement pkg, ResponseDTO annotation) {
        var bag = new DefaultDatasetClassBag();
        var settings = new SettingsBag();
        var super_class = ((TypeElement) ((DeclaredType) clazz.asType()).asElement()).getSuperclass();

        bag.new_name = getNewSerializerName(clazz.getSimpleName().toString());
        bag.base_class = super_class.getKind() != TypeKind.NONE ? typeUtils.asElement(super_class) : null;
        bag.clazz = clazz;
        bag.pkg = pkg;

        settings.policy = annotation.policy();

        for(var current: ElementFilter.fieldsIn(clazz.getEnclosedElements())) {
            var type = getGenerics(current.asType());
            if (type != null) {
                var result = new FieldContainer();

                result.base = current;
                result.str_type = type.getRoot(false);
                result.str_type_annotations = type.getRoot(true);
                result.root_type = type.fullRoot;
                result.str_name = current.getSimpleName().toString();
                result.str_access = current.getModifiers().stream().map(e -> e.toString().toLowerCase(Locale.ROOT)).collect(Collectors.joining(" "));
                result.annotations = type.getAnnotations();

                bag.fields.add(result);
            }
        }

        bag.imports.add("com.fasterxml.jackson.databind.ser.std.StdSerializer");
        bag.imports.add("com.fasterxml.jackson.databind.SerializerProvider");
        bag.imports.add("com.fasterxml.jackson.core.JsonGenerator");
        bag.imports.add("java.io.IOException");

        makeDefaultSerializer(bag, settings);
    }

    private void makeDefaultResponseFrom(Element clazz, Boolean serializer_enabled, ResponseDTO annotation, PackageElement pkg) throws Exception {
        var bag = new DefaultDatasetClassBag();
        var parent_imports = getImports(clazz);
        var fields = new HashMap<String, FieldContainer>();
        var classElement = ((TypeElement) ((DeclaredType) clazz.asType()).asElement());
        var superClazz = classElement.getSuperclass();

        bag.new_name = getNewClassName(clazz.getSimpleName().toString());
        bag.base_class = superClazz.getKind() != TypeKind.NONE ? typeUtils.asElement(superClazz) : null;
        bag.imports = new ArrayList<>(List.of("java.util.Optional"));
        bag.clazz = clazz;
        bag.pkg = pkg;

        for(var current: ElementFilter.fieldsIn(clazz.getEnclosedElements())) {
            var type = getGenerics(current.asType());

            if (type != null) {
                var result = new FieldContainer();

                result.base = current;
                result.str_type = type.getRoot(false);
                result.str_type_annotations = type.getRoot(true);
                result.root_type = type.fullRoot;
                result.str_name = current.getSimpleName().toString();
                result.str_access = current.getModifiers().stream().map(e -> e.toString().toLowerCase(Locale.ROOT)).collect(Collectors.joining(" "));
                result.annotations = type.getAnnotations();

                fields.put(result.str_name, result);

                bag.imports.addAll(type.getImports());
                bag.fields.add(result);
            }
        }

        if(bag.base_class != null) {
            var type = typeUtils.asElement(bag.base_class.asType());

            if(type instanceof TypeElement) {
                var typeElement = (TypeElement) type;

                if(!typeElement.getSimpleName().toString().equals("Object")) {
                    bag.imports.add(typeElement.getQualifiedName().toString());
                }
            }
        }

        for(var constructor: APUtils.getTypeMirrorFromAnnotationValue(() -> annotation.value())) {
            var computed = createConstructorFor(
                (TypeElement) typeUtils.asElement(constructor),
                fields,
                bag.new_name,
                parent_imports,
                clazz,
                serializer_enabled
            );

            bag.constructors.add(computed.data);
            bag.imports.addAll(computed.imports);
        }

        if(serializer_enabled) {
            bag.imports.add("com.fasterxml.jackson.databind.annotation.JsonSerialize");
        } else {
            bag.imports.remove("java.util.Optional");
        }

        makeDefaultResponseClass(bag, serializer_enabled);
    }

    private void makeDefaultResponseClass(DefaultDatasetClassBag bag, Boolean serializer_enabled) throws IOException {
        var file = filter.createSourceFile(bag.pkg.getQualifiedName() + "." + bag.new_name);

        try (var w = file.openWriter()) {
            var pack = bag.pkg.getQualifiedName().toString();
            var pw = new PrintWriter(w);

            pw.println("package " + pack + ";");
            pw.print("\n");

            for(var field: bag.imports.stream().filter(e -> !e.startsWith("java.lang.") && e.contains(".")).distinct().collect(Collectors.toList())) {
                pw.println("import " + field + ";");
            }

            pw.println("\n/**");
            pw.println(" * ?????????????????????????? ?????????????????????????? ?? ?????????????? dto-optional-annotation-preprocessor");
            pw.println(" * ???????? ?????????? ?????????? ???????????????????????? ?????? ?????????? ??????????????, ?????? ???????????????????????? ???????? ????????????????????????");
            pw.println(" * ?????? ???????????????????? Data Transfer Object ?????? ????????????. ???????????????????????? ???? ???????????? @see {@link " + bag.clazz.getSimpleName() + "}");
            pw.println(" */");

            if(serializer_enabled) {
                pw.println("@JsonSerialize(using = " + getNewSerializerName(bag.clazz.getSimpleName().toString()) + ".class)");
            }

            pw.print("public class " + bag.new_name);

            if(bag.clazz instanceof TypeElement) {
                var tClazz = (TypeElement) bag.clazz;
                var modifiers = tClazz.getModifiers();
                var constructors = ElementFilter.constructorsIn(tClazz.getEnclosedElements());

                if(bag.clazz == null) {
                    throw new Exception("No executable class . [" + bag.new_name + "]");
                }

                if(bag.clazz.getSimpleName().toString().equals("Object")) {
                    throw new Exception("Base class cannot be object. [" + bag.clazz.getSimpleName() + "]");
                }

                if(modifiers.contains(Modifier.FINAL)) {
                    throw new Exception("DTO class cannot be final. [" + bag.clazz.getSimpleName() + "]");
                }

                if(modifiers.contains(Modifier.PRIVATE)) {
                    throw new Exception("DTO class cannot be private. [" + bag.clazz.getSimpleName() + "]");
                }

                if(constructors.stream().noneMatch(e -> e.getParameters().size() == 0)) {
                    throw new Exception("No default constrictor for base class. [" + bag.clazz.getSimpleName() + "]");
                }

                pw.print(" extends " + bag.clazz.getSimpleName());
            }

            pw.println(" {");

            if(bag.fields.size() > 0) {
                for (var field : bag.fields) {
                    if(serializer_enabled) {
                        pw.println("\t" + field.str_access + " Optional<" + field.str_type_annotations + "> " + field.str_name + ";");
                    } else {
                        pw.println("\t" + field.str_access + " " + field.str_type_annotations + " " + field.str_name + ";");
                    }
                }

                pw.print("\n");
            }

            pw.println("\tpublic " + bag.new_name + "() {");

            if(bag.clazz instanceof TypeElement) {
                pw.println("\t\tsuper();\n");
            }

            for(var field: bag.fields) {
                var constant = field.base.getConstantValue();

                pw.print("\t\tthis.");
                pw.print(field.str_name);
                pw.print(" = ");
                pw.print((constant == null ? null : (serializer_enabled ? "Optional.ofNullable(" : "") + (constant instanceof String ? "\"" + constant + "\"" : constant) + (serializer_enabled ? ")" : "")));
                pw.println(";");
            }

            pw.println("\t}");

            for(var constructor: bag.constructors) {
                pw.print("\n");
                pw.println(constructor);
            }

            for(var field: bag.fields) {
                if(serializer_enabled) {
                    pw.print("\n");
                    pw.println("\tpublic Boolean has" + APUtils.camelCase(field.str_name) + "Field() {");
                    pw.println("\t\treturn this." + field.str_name + " != null ? true : false;");
                    pw.println("\t}");
                }

                pw.print("\n");
                pw.println("\tpublic " + field.str_type_annotations + " get" + APUtils.camelCase(field.str_name) + "(" + field.str_type + " def) {");

                if(serializer_enabled) {
                    pw.println("\t\treturn this." + field.str_name + " != null ? this." + field.str_name + ".orElse(def) : def;");
                } else {
                    pw.println("\t\treturn this." + field.str_name + " != null ? this." + field.str_name + " : def;");
                }

                pw.println("\t}");
                pw.print("\n");
                pw.println("\tpublic " + field.str_type_annotations + " get" + APUtils.camelCase(field.str_name) + "() {");
                pw.println("\t\treturn this.get" + APUtils.camelCase(field.str_name) + "(null);");
                pw.println("\t}");
                pw.print("\n");

                if(!field.base.getModifiers().contains(Modifier.FINAL)) {
                    pw.println("\tpublic void clear" + APUtils.camelCase(field.str_name) + "() {");
                    pw.println("\t\tthis." + field.str_name + " = null;");
                    pw.println("\t}");
                    pw.print("\n");

                    if(serializer_enabled) {
                        pw.println("\tpublic void set" + APUtils.camelCase(field.str_name) + "(final " + field.str_type + " value) {");
                        pw.println("\t\tthis." + field.str_name + " = Optional.ofNullable(value);");
                        pw.println("\t}");
                    } else {
                        pw.println("\tpublic void set" + APUtils.camelCase(field.str_name) + "(final " + field.str_type + " value) {");
                        pw.println("\t\tthis." + field.str_name + " = value;");
                        pw.println("\t}");
                    }
                }
            }

            pw.println("}");
            pw.flush();
        } catch (Exception x) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, x.toString());
        }
    }

    private void makeDefaultSerializer(DefaultDatasetClassBag bag, SettingsBag settings){
        try {
            var f = filter.createSourceFile(bag.pkg.getQualifiedName() + "." + bag.new_name);

            try (var w = f.openWriter()) {
                var from_name = getNewClassName(bag.clazz.getSimpleName().toString());
                var pack = bag.pkg.getQualifiedName().toString();
                var pw = new PrintWriter(w);

                pw.println("package " + pack + ";");
                pw.print("\n");

                for(var field: bag.imports.stream().filter(e -> !e.startsWith("java.lang.") && e.contains(".")).distinct().collect(Collectors.toList())) {
                    pw.println("import " + field + ";");
                }

                pw.println("\n/**");
                pw.println(" * ?????????????????????????? ?????????????????????????? ?? ?????????????? dto-optional-annotation-preprocessor");
                pw.println(" * ???????? ?????????? ???????????????????????? ?????? ???????????????????????? ?????????????? @see {@link " + from_name + "}.");
                pw.println(" * ???????????????????????? ???? ???????????? @see {@link " + bag.clazz.getSimpleName() + "}");
                pw.println(" */");

                pw.println("class " + bag.new_name + " extends StdSerializer<" + from_name +"> {");
                pw.println("\tpublic " + bag.new_name + "() {");
                pw.println("\t\tsuper(" + from_name + ".class);");
                pw.println("\t}");
                pw.print("\n");
                pw.println("\tpublic " + bag.new_name + "(Class<" + from_name + "> from) {");
                pw.println("\t\tsuper(from);");
                pw.println("\t}");

                pw.print("\n");
                pw.println("\t@Override");
                pw.println("\tpublic void serialize(" + from_name + " value, JsonGenerator gen, SerializerProvider provider) throws IOException {");
                pw.println(buildThree(bag.clazz, settings));
                pw.println("\t}");

                pw.println("}");
                pw.flush();
            } catch (IOException x) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, x.toString());
            }
        } catch (IOException x) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, x.toString());
        }
    }

    private String getNewRequestClassName(String input) {
        return input.endsWith("DTO") ? input.substring(0, input.length() - 3) + "RequestDTO" : input + "RequestDTO";
    }

    private String getNewClassName(String input) {
        return input.endsWith("DTO") ? input.substring(0, input.length() - 3) + "ResponseDTO" : input + "ResponseDTO";
    }

    private String getNewSerializerName(String input) {
        return getNewClassName(input) + "Serializer";
    }

    private String buildThree(Element from, SettingsBag settings){
        var builder = new StringBuilder();
        var char_trip = "\t\t";
        var from_fields = ElementFilter.fieldsIn(from.getEnclosedElements()).stream().collect(Collectors.toMap(e -> e.getSimpleName().toString(), e -> e));

        builder.append(char_trip).append("gen.writeStartObject();\n\n");

        for(var field: from_fields.values()) {
            var getter = APUtils.toGetter(field.getSimpleName().toString());
            var custom_name = APUtils.convertToCurrentCase(field.getSimpleName().toString(), settings.policy);
            var build_with_field = buildWithTypeOf(field.asType());

            builder.append(char_trip).append("if(value.").append("has").append(APUtils.camelCase(field.getSimpleName().toString())).append("Field").append("()) {\n");
            builder.append(char_trip).append("\tif(value.").append(getter).append("() != null) {\n");

            if (build_with_field != null) {
                builder
                    .append(char_trip)
                    .append("\t\t")
                    .append(build_with_field)
                    .append("(\"")
                    .append(custom_name)
                    .append("\", ")
                    .append("value.")
                    .append(getter)
                .append("());\n");
            } else {
                builder
                    .append(char_trip)
                    .append("\t\t")
                    .append("provider.defaultSerializeField(\"")
                    .append(custom_name)
                    .append("\", ")
                    .append("value.")
                    .append(getter)
                .append("(), gen);\n");
            }

            builder
                .append(char_trip)
                .append("\t} else {\n")
                .append(char_trip)
                .append("\t\tgen.writeNullField(\"")
                .append(custom_name)
                .append("\");\n")
                .append(char_trip)
                .append("\t}\n")
                .append(char_trip)
            .append("}\n\n");
        }

        builder.append(char_trip).append("gen.writeEndObject();");

        return builder.toString();
    }

    private String buildWithTypeOf(TypeMirror type) {
        var el_type = typeUtils.asElement(type);

        if(el_type instanceof TypeElement) {
            var t_el_type = (TypeElement) el_type;

            switch (t_el_type.getQualifiedName().toString()) {
                case "java.lang.String": return "gen.writeStringField";
                case "java.lang.Boolean": return "gen.writeBooleanField";
                case "java.lang.Float":
                case "java.lang.Short":
                case "java.lang.Double":
                case "java.lang.Long":
                case "java.lang.Integer": return "gen.writeNumberField";
            }
        }

        return null;
    }

    private ConstructorBag createConstructorFor(
        TypeElement element,
        HashMap<String, FieldContainer> fields,
        String constructor_name,
        List<String> parent_imports,
        Element clazz,
        Boolean serializer_enabled
    ) {
        var bag = new ConstructorBag();
        var builder = new StringBuilder();
        var object_fields = ElementFilter.fieldsIn(element.getEnclosedElements()).stream().collect(Collectors.toMap(e -> e.getSimpleName().toString(), e -> e));
        var object_methods = ElementFilter.methodsIn(element.getEnclosedElements()).stream().collect(Collectors.toMap(e -> e.getSimpleName().toString(), e -> e));
        var simple_name = element.getSimpleName().toString();
        var conversion_name = element.getAnnotation(RequestDTO.class) != null ? getNewRequestClassName(simple_name) : simple_name;
        var skip_count = 0;

        builder.append("\tpublic ")
            .append(constructor_name)
            .append("(")
            .append(conversion_name)
            .append(" from)")
        .append(" {\n\t\tthis();\n\n\t\tif(from != null) {");

        //
        // ?????????????????????? ???????????????????? ??????????????????
        //
        for(var field: object_fields.values()) {
            var getter_signature = APUtils.toGetter(field.getSimpleName().toString());
            var getter = object_methods.get(getter_signature);
            var mirror_signature = fields.keySet().stream().filter(e -> e.startsWith(field.getSimpleName().toString())).min(Comparator.comparingInt(String::length)).orElse("");
            var mirror_rest = mirror_signature.length() > field.getSimpleName().length() ? mirror_signature.substring(field.getSimpleName().length()) : "";
            var mirror = fields.get(mirror_signature);

            //
            // ?????????????? ?????????????????? ????????
            //
            if(
                mirror != null && !mirror.base.getModifiers().contains(Modifier.FINAL) && (
                    getter != null && getter.getModifiers().contains(Modifier.PUBLIC) ||
                    element.getAnnotation(RequestDTO.class) != null && (
                        field.getModifiers().contains(Modifier.PRIVATE) ||
                        field.getModifiers().contains(Modifier.PROTECTED)
                    )
                )
            ) {
                //
                // ???????????????????? ???????????????? ???? null ?? ???????? ????????.
                //
                var skip_null_check = field.getAnnotation(SkipNullCheck.class) != null || mirror.base.getAnnotation(SkipNullCheck.class) != null;
                //
                // ???????????????????????? ??????
                //
                var return_type = getter != null ? getter.getReturnType() : field.asType();

                if(mirror_rest.length() != 0) {
                    //
                    // _id => id
                    // Id => id
                    //
                    if(mirror_rest.length() > 1) {
                        mirror_rest = mirror_rest.contains("_") ? mirror_rest.substring(1) : mirror_rest;
                        mirror_rest = mirror_rest.substring(0, 1).toLowerCase(Locale.ROOT) + mirror_rest.substring(1);
                    }

                    if(return_type.getKind() != TypeKind.VOID) {
                        var return_type_element = typeUtils.asElement(return_type);

                        if (return_type_element instanceof TypeElement) {
                            var type = (TypeElement) return_type_element;
                            var rest_getter = APUtils.toGetter(mirror_rest);
                            var get_rest = ElementFilter.methodsIn(type.getEnclosedElements()).stream().filter(e -> e.getSimpleName().toString().equals(rest_getter)).findFirst().orElse(null);

                            if (getter == null || getter.getParameters().size() == 0 && get_rest != null && ((TypeElement) typeUtils.asElement(get_rest.getReturnType())).getQualifiedName().toString().equals(mirror.root_type)) {
                                builder
                                    .append("\n\t\t\tthis.set")
                                    .append(APUtils.camelCase(mirror.str_name))
                                    .append("(Objects.requireNonNullElse(from.")
                                    .append(getter_signature)
                                    .append("(), new ")
                                    .append(type.getSimpleName())
                                    .append("()).")
                                    .append(rest_getter)
                                .append("());");

                                bag.imports.add(type.getQualifiedName().toString());
                                bag.imports.add("java.util.Objects");
                            }
                        }
                    }
                } else if(return_type.getKind() != TypeKind.VOID) {
                    var return_type_element = typeUtils.asElement(return_type);

                    if (return_type_element instanceof TypeElement) {
                        var type_return_element = (TypeElement) return_type_element;

                        if (getter == null || getter.getParameters().size() == 0) {
                            if(type_return_element.getQualifiedName().toString().equals(mirror.root_type)) {
                                var mirror_type = mirror.base.asType();

                                if(
                                    mirror_type instanceof DeclaredType &&
                                    return_type instanceof DeclaredType &&
                                    //
                                    // ???????????????????????? ?????? - java.util.stream.Stream, ?????? ???????????? stream ?? ?????? ???? ???????????? ??????????
                                    //
                                    List.of(
                                        "java.util.LinkedHashSet",
                                        "java.util.HashSet",
                                        "java.util.EnumSet",
                                        "java.util.TreeSet",
                                        "java.util.Set",
                                        "java.util.LinkedList",
                                        "java.util.ArrayList",
                                        "java.util.List"
                                    ).contains(type_return_element.getQualifiedName().toString())
                                ) {
                                    var mirror_declared_type = (DeclaredType) mirror_type;
                                    var mirror_declared_first_generic = mirror_declared_type.getTypeArguments().stream().findFirst().orElse(null);
                                    var return_declared_type = (DeclaredType) return_type;
                                    var return_declared_first_generic = return_declared_type.getTypeArguments().stream().findFirst().orElse(null);

                                    if(mirror_declared_first_generic instanceof DeclaredType && return_declared_first_generic instanceof DeclaredType) {
                                        var mirror_declared_type_generic = (DeclaredType) mirror_declared_first_generic;
                                        var return_declared_type_generic = (DeclaredType) return_declared_first_generic;
                                        var mirror_declared_type_element = (TypeElement) mirror_declared_type_generic.asElement();
                                        var return_declared_type_element = (TypeElement) return_declared_type_generic.asElement();
                                        var mirror_declared_type_element_str = mirror_declared_type_element.getQualifiedName().toString();
                                        var return_declared_type_element_str = return_declared_type_element.getQualifiedName().toString();

                                        if(mirror_declared_type_element_str.equals(return_declared_type_element_str) && return_declared_type_element.getAnnotation(RequestDTO.class) == null) {
                                            switch (type_return_element.getQualifiedName().toString()) {
                                                case "java.util.LinkedHashSet", "java.util.HashSet", "java.util.EnumSet", "java.util.TreeSet", "java.util.Set" -> builder
                                                        .append(skip_null_check ? "" : "\n\t\t\tif(from.")
                                                        .append(skip_null_check ? "" : APUtils.toGetter(mirror.str_name))
                                                        .append(skip_null_check ? "" : "() != null) {")
                                                        .append("\n\t\t\t")
                                                        .append(skip_null_check ? "" : "\t")
                                                        .append("this.")
                                                        .append(APUtils.toSetter(mirror.str_name))
                                                        .append("((")
                                                        .append(mirror.str_type)
                                                        .append(") from.")
                                                        .append(getter_signature)
                                                        .append("().clone());")
                                                    .append(skip_null_check ? "" : "\n\t\t\t}");
                                                case "java.util.LinkedList", "java.util.ArrayList", "java.util.List" -> {
                                                    builder
                                                        .append(skip_null_check ? "" : "\n\t\t\tif(from.")
                                                        .append(skip_null_check ? "" : APUtils.toGetter(mirror.str_name))
                                                        .append(skip_null_check ? "" : "() != null) {")
                                                        .append("\n\t\t\t")
                                                        .append(skip_null_check ? "" : "\t")
                                                        .append("this.")
                                                        .append(APUtils.toSetter(mirror.str_name))
                                                        .append("(new ArrayList<>(from.")
                                                        .append(getter_signature)
                                                        .append("()));")
                                                    .append(skip_null_check ? "" : "\n\t\t\t}");

                                                    bag.imports.add("java.util.ArrayList");
                                                }
                                            }
                                        } else {
                                            //
                                            // ?????? ???????????????? ????-???? ?????? ?? ????????
                                            //
                                            var base_type = ElementFilter.constructorsIn(mirror_declared_type_element.getEnclosedElements()).stream().filter(e -> {
                                                //
                                                // ?????? ?? ???????? ?????????????????? ????????????????????????, ?? ?????????????????? ???? ?? ???????????????????????? ?????????? ??????????????, ???????? ?????????? ?????????????????????? ?? ?????????? ?????????? ?????? ????
                                                //
                                                var param = e.getParameters().stream().findFirst().orElse(null);

                                                if (param != null) {
                                                    var type_param = typeUtils.asElement(param.asType());

                                                    if (type_param instanceof TypeElement) {
                                                        var type_param_element = (TypeElement) type_param;

                                                        // ?????? ?????????????? ?????????????????? ???????????????????????? == ?????????????? ?????????????????????????? ???????????????? ????????
                                                        return type_param_element.getQualifiedName().toString().equals(return_declared_type_element_str);
                                                    }
                                                }

                                                return false;
                                            }).findFirst();
                                            //
                                            // ???????? ???????? ?????????????????? ResponseDTO ???????????? ???? ?????????? ???????????? ?????????? ???????????????????????? ?????????? ??????????, ?????? ???????????? ?? ?????????? ???????????????????????? ?????? ????????????????
                                            //
                                            var response_dto_annotation = mirror_declared_type_element.getAnnotation(ResponseDTO.class);

                                            //
                                            // ???????? ?????? ???????????? ???????????????????? ?????????????????????? ?????? ???????????????? ???????? ???????? ?????????????????? responseDTO
                                            //
                                            if(
                                                base_type.isPresent() || response_dto_annotation != null &&
                                                    APUtils.getTypeMirrorFromAnnotationValue(() -> response_dto_annotation.value()).stream()
                                                        .map(e -> ((TypeElement) typeUtils.asElement(e)).getQualifiedName())
                                                        .anyMatch(e -> e.toString().equals(return_declared_type_element_str))
                                            ) {
                                                var name = mirror_declared_type_element.getSimpleName().toString();

                                                builder
                                                    .append(skip_null_check ? "" : "\n\t\t\tif(from.")
                                                    .append(skip_null_check ? "" : APUtils.toGetter(mirror.str_name))
                                                    .append(skip_null_check ? "" : "() != null) {")
                                                    .append("\n\t\t\t")
                                                    .append(skip_null_check ? "" : "\t")
                                                    .append("this.")
                                                    .append(APUtils.toSetter(mirror.str_name))
                                                    .append("(from.")
                                                    .append(APUtils.toGetter(mirror.str_name))
                                                    .append("().stream().map(")
                                                    .append(response_dto_annotation != null ? getNewClassName(name) : name)
                                                    .append("::new")
                                                .append(").collect(Collectors.");

                                                switch (type_return_element.getQualifiedName().toString()) {
                                                    case "java.util.LinkedHashSet", "java.util.HashSet", "java.util.EnumSet", "java.util.TreeSet", "java.util.Set" -> builder.append("toSet");
                                                    case "java.util.LinkedList", "java.util.ArrayList", "java.util.List" -> builder.append("toList");
                                                }

                                                builder.append("()));");
                                                builder.append(skip_null_check ? "" : "\n\t\t\t}");

                                                bag.imports.add(mirror_declared_type_element.getQualifiedName().toString());
                                                bag.imports.add("java.util.stream.Collectors");
                                            } else {
                                                skip_count++;
                                            }
                                        }
                                    } else {
                                        skip_count++;
                                    }
                                } else {
                                    builder.append("\n\t\t\tthis.").append(APUtils.toSetter(mirror.str_name)).append("(from.").append(getter_signature).append("());");
                                }
                            } else {
                                //
                                // ???????? ??????, ?????????????? ??????????, ?? ?????? ?? ???????? ?????????????????????? ?? ?????????? ?????????????? ????????.
                                //
                                var mirror_type = typeUtils.asElement(mirror.base.asType());

                                if(mirror_type instanceof TypeElement) {
                                    var t_mirror_type = (TypeElement) mirror_type;
                                    //
                                    // ?????????????????? ???????????? ?????? ???????? ?????????????? ???????????? ???????? ??????????????????
                                    //
                                    var type_return_element_str = type_return_element.getQualifiedName().toString();
                                    //
                                    // ?????????????? ??????????????????????, ?????????????? ???????????? ???????????????????? ?????????????????? ???????????????????????? ??????
                                    //
                                    var base_type = ElementFilter.constructorsIn(t_mirror_type.getEnclosedElements()).stream().filter(e -> {
                                        var param = e.getParameters().stream().findFirst().orElse(null);

                                        if (param != null) {
                                            var type_param = typeUtils.asElement(param.asType());

                                            if (type_param instanceof TypeElement) {
                                                return ((TypeElement) type_param).getQualifiedName().toString().equals(type_return_element_str);
                                            }
                                        }

                                        return false;
                                    }).findFirst();
                                    //
                                    // ???????????? ??????, ???????? ?????? ?????????????? ?????????????????????????? ???????? ???????? ?????????????????? ResponseDTO ?????? ???????????? ?????? ???? ?????????? ???????????? ?????????? ???????????????????????? ?????????? ??????????, ?? ?????????? ???????????? ?????? ?????? ??????????
                                    // ???????????? ?????????????????? ???????????????? ???? value() ???????? ??????????, ?????? ???????? ????????????????, ???? ?? ?????????????????????? ???? ???????? ???????? ?????????? ???????????????????????? ??????????????????????
                                    //
                                    var request_dto_annotation = t_mirror_type.getAnnotation(ResponseDTO.class);

                                    if(
                                        base_type.isPresent() || request_dto_annotation != null &&
                                            APUtils.getTypeMirrorFromAnnotationValue(() -> request_dto_annotation.value()).stream()
                                                .map(e -> ((TypeElement) typeUtils.asElement(e)).getQualifiedName()).anyMatch(e -> e.toString().equals(type_return_element_str))
                                    ) {
                                        var name = t_mirror_type.getSimpleName().toString();

                                        builder
                                            .append(skip_null_check ? "" : "\n\t\t\tif(from.")
                                            .append(skip_null_check ? "" : APUtils.toGetter(mirror.str_name))
                                            .append(skip_null_check ? "" : "() != null) {")
                                            .append("\n\t\t\t")
                                            .append(skip_null_check ? "" : "\t")
                                            .append("this.")
                                            .append(APUtils.toSetter(mirror.str_name))
                                            .append("(new ")
                                            .append(request_dto_annotation != null ? getNewClassName(name) : name)
                                            .append("(from.")
                                            .append(getter_signature)
                                            .append("()));")
                                        .append(skip_null_check ? "" : "\n\t\t\t}");

                                        bag.imports.add(t_mirror_type.getQualifiedName().toString());
                                    } else {
                                        skip_count++;
                                    }
                                } else {
                                    skip_count++;
                                }
                            }
                        } else {
                            skip_count++;
                        }
                    } else {
                        skip_count++;
                    }
                } else {
                    skip_count++;
                }
            } else {
                skip_count++;
            }
        }

        //
        // ?????? ?????? ?????????????????? ??????????????????????, ?????????????? ?????????????????? ??????????-???? ??????
        //
        for(var constructor: ElementFilter.constructorsIn(clazz.getEnclosedElements())) {
            var param = constructor.getParameters().stream().findFirst().orElse(null);

            if(param != null) {
                var type_param = typeUtils.asElement(param.asType());

                if(type_param instanceof TypeElement) {
                    var type_param_element = (TypeElement) type_param;

                    if(type_param_element.getQualifiedName() == element.getQualifiedName()) {
                        var tree = trees.getTree(constructor);
                        var param_name = detectMethodParams(tree.getParameters()).stream().findFirst().orElse(null);

                        for(var line: tree.getBody().getStatements()) {
                            builder.append("\n\t\t\t").append(replaceAllLinks(line, param_name, serializer_enabled, clazz.getSimpleName().toString(), constructor_name));
                        }

                        bag.imports.addAll(getIntersectOf(constructor, parent_imports));
                    }
                }
            }
        }

        if(skip_count > 0) {
            System.out.println("[WARN] When creating the converter " + constructor_name + " -> " + conversion_name + " " + skip_count + " fields were omitted");
        }

        builder.append("\n\t\t}\n\t}");

        bag.data = builder.toString();
        bag.imports.add(element.getQualifiedName().toString());

        return bag;
    }

    private GenericBag getGenerics(TypeMirror from) {
        var root = typeUtils.asElement(from);

        if(root instanceof TypeElement) {
            var type = (TypeElement) root;
            var qualified = type.getQualifiedName().toString();
            var type_annotation = type.getAnnotation(ResponseDTO.class);
            var bag = new GenericBag();

            if(type_annotation != null) {
                bag.simpleRoot = getNewClassName(type.getSimpleName().toString());
                bag.fullRoot = getNewClassName(qualified);
            } else {
                bag.simpleRoot = type.getSimpleName().toString();
                bag.fullRoot = qualified;
            }

            if(!bag.provided_imports.contains(bag.fullRoot)) {
                bag.provided_imports.add(bag.fullRoot);
            }

            if(from instanceof DeclaredType) {
                var declared = (DeclaredType) from;

                for (var mirror : declared.getTypeArguments()) {
                    var generic = getGenerics(mirror);

                    if(generic != null) {
                        bag.generics.add(generic);
                    }
                }

                bag.annotations.addAll(declared.getAnnotationMirrors().stream().map(this::handleAnnotation).collect(Collectors.toList()));
            }

            return bag;
        } else {
            return null;
        }
    }

    private Annotation handleAnnotation(AnnotationMirror e) {
        var annotation = new Annotation();
        var values = e.getElementValues();

        annotation.provided_imports = new ArrayList<>(List.of(e.getAnnotationType().toString()));
        annotation.name = e.getAnnotationType().asElement().getSimpleName().toString();

        for (var el : e.getElementValues().keySet()) {
            annotation.params.put(el.getSimpleName().toString(), values.get(el).toString());
        }

        return annotation;
    }

    private List<String> detectMethodParams(List<? extends VariableTree> params) {
        var result = new ArrayList<String>();

        for(var param: params) {
            var scanner = new CodeMethodParametersAnalyzer();

            scanner.scan(param, null);

            result.add(scanner.getName());
        }

        return result;
    }

    private String replaceAllLinks(StatementTree tree, String statement, Boolean serializer_enabled, String fromClassName, String toClassName) {
        var value = tree.toString().split("\"");

        for(var i = 0; i < value.length; i++) {
            value[i] = i % 2 == 0 ? value[i].replaceAll(fromClassName, toClassName) : value[i];
        }

        var result = String.join("\"", value).replaceAll("([^.]|^)(" + statement + ")([^(]|$)", "$1from$3");

        if(serializer_enabled) {
            result = CallbackMatcher.init("this\\.([aA-zZ0-9_$]+)\\s*=\\s*([^;]*)").findMatches(
                result,
                match -> "this." + APUtils.toSetter(match.group(1)) + "(" + match.group(2) + ");"
            );
        }

        return result;
    }

    private List<String> getIntersectOf(ExecutableElement method, List<String> imports) {
        var scanner = new CodeIdentifierUsageAnalyzer(imports);
        var path = trees.getPath(method);

        scanner.scan(path, trees);

        return scanner.getUseful();
    }

    private List<String> getImports(Element clazz) {
        var scanner = new CodeImportsAnalyzer();
        var path = trees.getPath(clazz);

        scanner.scan(path, null);

        return scanner.getImports();
    }
}
