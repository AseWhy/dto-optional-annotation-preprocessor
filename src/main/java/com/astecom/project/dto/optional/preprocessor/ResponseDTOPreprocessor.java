package com.astecom.project.dto.optional.preprocessor;

import com.astecom.project.dto.optional.preprocessor.annotations.RequestDTO;
import com.astecom.project.dto.optional.preprocessor.annotations.ResponseDTO;
import com.astecom.project.dto.optional.preprocessor.members.*;
import com.astecom.project.dto.optional.preprocessor.scanner.CodeIdentifierUsageAnalyzer;
import com.astecom.project.dto.optional.preprocessor.scanner.CodeImportsAnalyzer;
import com.astecom.project.dto.optional.preprocessor.scanner.CodeMethodParametersAnalyzer;
import com.astecom.project.dto.optional.preprocessor.utils.APUtils;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.Trees;
import jdk.jfr.AnnotationElement;

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

@SupportedAnnotationTypes({ "com.astecom.project.dto.optional.preprocessor.annotations.ResponseDTO" })
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public class ResponseDTOPreprocessor extends AbstractProcessor {
    protected Trees trees;
    protected Types typeUtils;
    protected Elements elementUtils;
    protected Filer filter;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.trees = Trees.instance(processingEnv);
        this.typeUtils = processingEnv.getTypeUtils();
        this.elementUtils = processingEnv.getElementUtils();
        this.filter = processingEnv.getFiler();
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

            if(serializer_enabled) {
                makeDefaultSerializerFrom(clazz, pkg);
            }

            makeDefaultResponseFrom(clazz, serializer_enabled, annotation, pkg);
        }

        return true;
    }

    private void makeDefaultSerializerFrom(Element clazz, PackageElement pkg) {
        var bag = new DefaultDatasetClassBag();
        var super_class = ((TypeElement) ((DeclaredType) clazz.asType()).asElement()).getSuperclass();

        bag.new_name = getNewSerializerName(clazz.getSimpleName().toString());
        bag.base_class = super_class.getKind() != TypeKind.NONE ? typeUtils.asElement(super_class) : null;
        bag.clazz = clazz;
        bag.pkg = pkg;

        for(var current: ElementFilter.fieldsIn(clazz.getEnclosedElements())) {
            var type = getGenerics(current.asType());
            if (type != null) {
                var result = new FieldContainer();

                result.base = current;
                result.str_type = type.getRoot();
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

        makeDefaultSerializer(bag);
    }

    private void makeDefaultResponseFrom(Element clazz, Boolean serializer_enabled, ResponseDTO annotation, PackageElement pkg) {
        var bag = new DefaultDatasetClassBag();
        var parent_imports = getImports(clazz);
        var fields = new HashMap<String, FieldContainer>();
        var super_class = ((TypeElement) ((DeclaredType) clazz.asType()).asElement()).getSuperclass();

        bag.new_name = getNewClassName(clazz.getSimpleName().toString());
        bag.base_class = super_class.getKind() != TypeKind.NONE ? typeUtils.asElement(super_class) : null;
        bag.imports = new ArrayList<>(List.of("java.util.Optional"));
        bag.clazz = clazz;
        bag.pkg = pkg;

        for(var current: ElementFilter.fieldsIn(clazz.getEnclosedElements())) {
            var type = getGenerics(current.asType());

            if (type != null) {
                var result = new FieldContainer();

                result.base = current;
                result.str_type = type.getRoot();
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
            var computed = createConstructorFor((TypeElement) typeUtils.asElement(constructor), fields, bag.new_name, parent_imports, clazz);
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

    private void makeDefaultResponseClass(DefaultDatasetClassBag bag, Boolean serializer_enabled){
        try {
            var f = filter.createSourceFile(bag.pkg.getQualifiedName() + "." + bag.new_name);

            try (var w = f.openWriter()) {
                var pack = bag.pkg.getQualifiedName().toString();
                var pw = new PrintWriter(w);

                pw.println("package " + pack + ";");
                pw.print("\n");

                for(var field: bag.imports.stream().filter(e -> !e.startsWith("java.lang.") && e.contains(".")).distinct().collect(Collectors.toList())) {
                    pw.println("import " + field + ";");
                }

                pw.println("\n/**");
                pw.println(" * Сгенерировано автоматически с помощью dto-optional-annotation-preprocessor");
                pw.println(" * Этот класс можно использовать как ответ сервера, тут предусмотрен свой сериализатор");
                pw.println(" * Это реализация Data Transfer Object для ответа. Реализованно от класса @see {@link " + bag.clazz.getSimpleName() + "}");
                pw.println(" */");

                if(serializer_enabled) {
                    pw.println("@JsonSerialize(using = " + getNewSerializerName(bag.clazz.getSimpleName().toString()) + ".class)");
                }

                pw.print("public class " + bag.new_name);

                if(bag.base_class != null && !bag.base_class.getSimpleName().toString().equals("Object")) {
                    pw.print(" extends " + bag.base_class.getSimpleName());
                }

                pw.println(" {");

                if(bag.fields.size() > 0) {
                    for (var field : bag.fields) {
                        if(serializer_enabled) {
                            pw.println("\t" + field.str_access + " Optional<" + field.annotations + field.str_type + "> " + field.str_name + ";");
                        } else {
                            pw.println("\t" + field.str_access + " " + field.annotations + field.str_type + " " + field.str_name + ";");
                        }
                    }

                    pw.print("\n");
                }

                pw.println("\tpublic " + bag.new_name + "() {");

                for(var field: bag.fields) {
                    pw.println("\t\tthis." + field.str_name + " = null;");
                }

                pw.println("\t}");

                for(var constructor: bag.constructors) {
                    pw.print("\n");
                    pw.println(constructor);
                }

                for(var field: bag.fields) {
                    if(serializer_enabled) {
                        pw.print("\n");
                        pw.println("\tpublic Boolean has" + camelCase(field.str_name) + "Field() {");
                        pw.println("\t\treturn this." + field.str_name + " != null ? true : false;");
                        pw.println("\t}");
                    }

                    pw.print("\n");
                    pw.println("\tpublic " + field.str_type + " get" + camelCase(field.str_name) + "(" + field.str_type + " def) {");

                    if(serializer_enabled) {
                        pw.println("\t\treturn this." + field.str_name + " != null ? this." + field.str_name + ".orElse(def) : def;");
                        pw.println("\t}");
                        pw.print("\n");
                        pw.println("\tpublic " + field.str_type + " get" + camelCase(field.str_name) + "() {");
                        pw.println("\t\treturn this.get" + camelCase(field.str_name) + "(null);");
                        pw.println("\t}");
                        pw.print("\n");
                        pw.println("\tpublic void set" + camelCase(field.str_name) + "(final " + field.str_type + " value) {");
                        pw.println("\t\tthis." + field.str_name + " = Optional.ofNullable(value);");
                    } else {
                        pw.println("\t\treturn this." + field.str_name + " != null ? this." + field.str_name + " : def;");
                        pw.println("\t}");
                        pw.print("\n");
                        pw.println("\tpublic " + field.str_type + " get" + camelCase(field.str_name) + "() {");
                        pw.println("\t\treturn this.get" + camelCase(field.str_name) + "(null);");
                        pw.println("\t}");
                        pw.print("\n");
                        pw.println("\tpublic void set" + camelCase(field.str_name) + "(final " + field.str_type + " value) {");
                        pw.println("\t\tthis." + field.str_name + " = value;");
                    }

                    pw.println("\t}");
                }

                pw.println("}");
                pw.flush();
            } catch (IOException x) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, x.toString());
            }
        } catch (IOException x) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, x.toString());
        }
    }

    private void makeDefaultSerializer(DefaultDatasetClassBag bag){
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
                pw.println(" * Сгенерировано автоматически с помощью dto-optional-annotation-preprocessor");
                pw.println(" * Этот класс используется для сериализации объекта @see {@link " + from_name + "}.");
                pw.println(" * Реализованно от класса @see {@link " + bag.clazz.getSimpleName() + "}");
                pw.println(" */");

                pw.print("class " + bag.new_name + " extends StdSerializer<" + from_name +"> {");

                pw.print("\n");
                pw.println("\tpublic " + bag.new_name + "() {");
                pw.println("\t\tsuper((Class) null);");
                pw.println("\t}");
                pw.print("\n");
                pw.println("\tpublic " + bag.new_name + "(Class<" + from_name + "> from) {");
                pw.println("\t\tsuper(from);");
                pw.println("\t}");

                pw.print("\n");
                pw.println("\t@Override");
                pw.println("\tpublic void serialize(" + from_name + " value, JsonGenerator gen, SerializerProvider provider) throws IOException {");
                pw.println(buildThree(bag.clazz));
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

    private String camelCase(String input) {
        return input.substring(0, 1).toUpperCase(Locale.ROOT) + input.substring(1);
    }

    private String getNewRequestClassName(String input) {
        return input.endsWith("DTO") ? input.substring(0, input.length() - 3) + "RequestDTO" : input + "RequestDTO";
    }

    private String getNewClassName(String input) {
        return input.endsWith("DTO") ? input.substring(0, input.length() - 3) + "ResponseDTO" : input + "ResponseDTO";
    }

    private String getNewSerializerName(String input) {
        return input.endsWith("Serializer") ? input.substring(0, input.length() - 10) + "ResponseSerializer" : input + "ResponseSerializer";
    }

    private String buildThree(Element from){
        var builder = new StringBuilder();
        var char_trip = "\t\t";
        var from_fields = ElementFilter.fieldsIn(from.getEnclosedElements()).stream().collect(Collectors.toMap(e -> e.getSimpleName().toString(), e -> e));

        builder.append(char_trip).append("gen.writeStartObject();\n\n");

        for(var field: from_fields.values()) {
            var getter = "get" + camelCase(field.getSimpleName().toString());
            var build_with_field = buildWithTypeOf(field.asType());

            builder.append(char_trip).append("if(value.").append("has").append(camelCase(field.getSimpleName().toString())).append("Field").append("()) {\n");
            builder.append(char_trip).append("\tif(value.").append(getter).append("() != null) {\n");

            if (build_with_field != null) {
                builder
                    .append(char_trip)
                    .append("\t\t")
                    .append(build_with_field)
                    .append("(\"")
                    .append(field.getSimpleName())
                    .append("\", ")
                    .append("value.")
                    .append(getter)
                .append("());\n");
            } else {
                builder
                    .append(char_trip)
                    .append("\t\t")
                    .append("provider.defaultSerializeField(\"")
                    .append(field.getSimpleName())
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
                .append(field.getSimpleName())
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
        Element clazz
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
        .append(" {");

        //
        // Стандартный обработчик конверсии
        //
        for(var field: object_fields.values()) {
            var getter_signature = "get" + camelCase(field.getSimpleName().toString());
            var getter = object_methods.get(getter_signature);
            var mirror_signature = fields.keySet().stream().filter(e -> e.startsWith(field.getSimpleName().toString())).min(Comparator.comparingInt(String::length)).orElse("");
            var mirror_rest = mirror_signature.length() > field.getSimpleName().length() ? mirror_signature.substring(field.getSimpleName().length()) : "";
            var mirror = fields.get(mirror_signature);

            if(
                mirror != null && (
                    getter != null && getter.getModifiers().contains(Modifier.PUBLIC) ||
                    element.getAnnotation(RequestDTO.class) != null && (
                        field.getModifiers().contains(Modifier.PRIVATE) ||
                        field.getModifiers().contains(Modifier.PROTECTED)
                    )
                )
            ) {
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
                            var rest_getter = "get" + camelCase(mirror_rest);
                            var get_rest = ElementFilter.methodsIn(type.getEnclosedElements()).stream().filter(e -> e.getSimpleName().toString().equals(rest_getter)).findFirst().orElse(null);

                            if (getter == null || getter.getParameters().size() == 0 && ((TypeElement) typeUtils.asElement(Objects.requireNonNull(get_rest).getReturnType())).getQualifiedName().toString().equals(mirror.root_type)) {
                                builder
                                    .append("\n\t\tthis.set")
                                    .append(camelCase(mirror.str_name))
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
                                    // Возвращаемый тип - java.util.stream.Stream, имя метода stream и это не пустой метод
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
                                                case "java.util.LinkedHashSet":
                                                case "java.util.HashSet":
                                                case "java.util.EnumSet":
                                                case "java.util.TreeSet":
                                                case "java.util.Set":
                                                    builder
                                                        .append("\n\t\tthis.set")
                                                        .append(camelCase(mirror.str_name))
                                                        .append("((").append(mirror.str_type)
                                                        .append(") from.")
                                                        .append(getter_signature)
                                                    .append("().clone());");
                                                break;
                                                case "java.util.LinkedList":
                                                case "java.util.ArrayList":
                                                case "java.util.List":
                                                    builder
                                                        .append("\n\t\tthis.set")
                                                        .append(camelCase(mirror.str_name))
                                                        .append("(new ArrayList<>(from.")
                                                        .append(getter_signature)
                                                    .append("()));");

                                                    bag.imports.add("java.util.ArrayList");
                                                break;
                                            }
                                        } else {
                                            //
                                            // Тут примерно то-же что и ниже
                                            //
                                            var base_type = ElementFilter.constructorsIn(mirror_declared_type_element.getEnclosedElements()).stream().filter(e -> {
                                                //
                                                // Тут я беру аргументы конструктора, и сравниваю их с возвращаемым типом геттера, если нашел конструктор с таким типом ток ок
                                                //
                                                var param = e.getParameters().stream().findFirst().orElse(null);

                                                if (param != null) {
                                                    var type_param = typeUtils.asElement(param.asType());

                                                    if (type_param instanceof TypeElement) {
                                                        var type_param_element = (TypeElement) type_param;

                                                        // Тип первого параметра конструктора == подтипу возвращаемого геттером типа
                                                        return type_param_element.getQualifiedName().toString().equals(return_declared_type_element_str);
                                                    }
                                                }

                                                return false;
                                            }).findFirst();
                                            //
                                            // Если есть аннотация ResponseDTO значит по этому классу будет сгенерирован новый класс, его парсер и будет использовать как дочерний
                                            //
                                            var request_dto_annotation = mirror_declared_type_element.getAnnotation(ResponseDTO.class);

                                            //
                                            // Если был найден подходящий конструктор или целевого типа есть аннотация responseDTO
                                            //
                                            if(
                                                base_type.isPresent() || request_dto_annotation != null &&
                                                    APUtils.getTypeMirrorFromAnnotationValue(() -> request_dto_annotation.value()).stream()
                                                        .map(e -> ((TypeElement) typeUtils.asElement(e)).getQualifiedName()).anyMatch(e -> e.toString().equals(return_declared_type_element_str))
                                            ) {
                                                var camel = camelCase(mirror.str_name);
                                                var name = mirror_declared_type_element.getSimpleName().toString();

                                                builder
                                                    .append("\n\t\tthis.set")
                                                    .append(camel)
                                                    .append("(from.get")
                                                    .append(camel)
                                                    .append("().stream().map(")
                                                    .append(request_dto_annotation != null ? getNewClassName(name) : name)
                                                    .append("::new")
                                                .append(").collect(Collectors.");

                                                switch (type_return_element.getQualifiedName().toString()) {
                                                    case "java.util.LinkedHashSet":
                                                    case "java.util.HashSet":
                                                    case "java.util.EnumSet":
                                                    case "java.util.TreeSet":
                                                    case "java.util.Set":
                                                        builder.append("toSet");
                                                    break;
                                                    case "java.util.LinkedList":
                                                    case "java.util.ArrayList":
                                                    case "java.util.List":
                                                        builder.append("toList");
                                                    break;
                                                }

                                                builder.append("()));");

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
                                    builder.append("\n\t\tthis.set").append(camelCase(mirror.str_name)).append("(from.").append(getter_signature).append("());");
                                }
                            } else {
                                //
                                // Беру тип который нужен, и ищу у него конструктор с типом который есть.
                                //
                                var mirror_type = typeUtils.asElement(mirror.base.asType());

                                if(mirror_type instanceof TypeElement) {
                                    var t_mirror_type = (TypeElement) mirror_type;
                                    //
                                    // Запоминаю полное имя типа который должен быть возвращен
                                    //
                                    var type_return_element_str = type_return_element.getQualifiedName().toString();
                                    //
                                    // Получаю конструктор, который первым параметром принимает возвращаемый тип
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
                                    // Скажем так, если над классом возвращаемого типа есть аннотация ResponseDTO это значит что по этому классу будет сгенерирован новый класс, а этому значит что мне нужно
                                    // просто проверить содержит ли value() этот класс, ибо если содержит, то в последствии по нему тоже будет сгенерирован конструктор
                                    //
                                    var request_dto_annotation = t_mirror_type.getAnnotation(ResponseDTO.class);

                                    if(
                                        base_type.isPresent() || request_dto_annotation != null &&
                                            APUtils.getTypeMirrorFromAnnotationValue(() -> request_dto_annotation.value()).stream()
                                                .map(e -> ((TypeElement) typeUtils.asElement(e)).getQualifiedName()).anyMatch(e -> e.toString().equals(type_return_element_str))
                                    ) {
                                        var name = t_mirror_type.getSimpleName().toString();

                                        builder
                                            .append("\n\t\tthis.set")
                                            .append(camelCase(mirror.str_name))
                                            .append("(new ")
                                            .append(request_dto_annotation != null ? getNewClassName(name) : name)
                                            .append("(from.")
                                            .append(getter_signature)
                                        .append("()));");

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
        // Ищу уже созданный конструктор, который принимает такой-же тип
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
                            builder.append("\n\t\t").append(replaceAllLinks(line, param_name, "from"));
                        }

                        bag.imports.addAll(getIntersectOf(constructor, parent_imports));
                    }
                }
            }
        }

        if(skip_count > 0) {
            System.out.println("[WARN] When creating the converter " + constructor_name + " -> " + conversion_name + " " + skip_count + " fields were omitted");
        }

        builder.append("\n\t}");

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

    private String replaceAllLinks(StatementTree tree, String statement, String replacement) {
        return tree.toString().replaceAll("([^.]|^)(" + statement + ")([^(]|$)", "$1" + replacement + "$3");
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
