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

    private void makeDefaultSerializerFrom(Element clazz, PackageElement pkg, ResponseDTO annotation) throws Exception {
        var bag = new DefaultDatasetClassBag();
        var settings = new SettingsBag();
        var typeCazz = (TypeElement) ((DeclaredType) clazz.asType()).asElement();
        var superClass = typeCazz.getSuperclass();
        var clazzModifiers = clazz.getModifiers();
        var methods = ElementFilter.methodsIn(typeCazz.getEnclosedElements());

        bag.newName = getNewSerializerName(clazz.getSimpleName().toString());
        bag.baseClass = superClass.getKind() != TypeKind.NONE ? typeUtils.asElement(superClass) : null;
        bag.clazz = clazz;
        bag.pkg = pkg;

        settings.policy = annotation.policy();

        if(!clazzModifiers.contains(Modifier.ABSTRACT)) {
            throw new Exception("DTO class must be abstract. [" + bag.clazz.getSimpleName() + "]");
        }

        for(var current: ElementFilter.fieldsIn(clazz.getEnclosedElements())) {
            var currentTypeMirror = current.asType();
            var currentType = typeUtils.asElement(currentTypeMirror);
            var type = getGenerics(currentTypeMirror);

            if (type != null) {
                var result = new FieldContainer();
                var modifiers = current.getModifiers();

                result.base = current;
                result.strType = type.getRoot(false);
                result.strTypeAnnotations = type.getRoot(true);
                result.rootType = type.fullRoot;
                result.strName = current.getSimpleName().toString();
                result.strAccess = modifiers.stream().map(e -> e.toString().toLowerCase(Locale.ROOT)).collect(Collectors.joining(" "));
                result.annotations = type.getAnnotations();

                if(currentType instanceof TypeElement) {
                    var currentTypeElement = (TypeElement) currentType;
                    var setterType = currentTypeElement.getSimpleName().toString();

                    result.hasSuperGetter = APUtils.hasOverride(typeUtils, methods, "get" + APUtils.camelCase(result.strName), setterType);
                    result.hasSuperSetter = APUtils.hasOverride(typeUtils, methods, "set" + APUtils.camelCase(result.strName), "void", setterType);

                    if(result.hasSuperGetter || result.hasSuperSetter) {
                        bag.imports.add("java.lang.Override");
                    }
                }

                bag.fields.add(result);

                if(modifiers.contains(Modifier.PUBLIC)) {
                    throw new Exception("DTO field cannot be public. [" + bag.clazz.getSimpleName() + "]");
                }
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
        var typeCazz = (TypeElement) ((DeclaredType) clazz.asType()).asElement();
        var clazzModifiers = clazz.getModifiers();
        var superClazz = typeCazz.getSuperclass();
        var methods = ElementFilter.methodsIn(typeCazz.getEnclosedElements());

        bag.newName = getNewClassName(clazz.getSimpleName().toString());
        bag.baseClass = superClazz.getKind() != TypeKind.NONE ? typeUtils.asElement(superClazz) : null;
        bag.imports = new ArrayList<>(List.of("java.util.Optional"));
        bag.clazz = clazz;
        bag.pkg = pkg;

        if(!clazzModifiers.contains(Modifier.ABSTRACT)) {
            throw new Exception("DTO class must be abstract. [" + bag.clazz.getSimpleName() + "]");
        }

        for(var current: ElementFilter.fieldsIn(clazz.getEnclosedElements())) {
            var currentTypeMirror = current.asType();
            var currentType = typeUtils.asElement(currentTypeMirror);
            var type = getGenerics(currentTypeMirror);

            if (type != null) {
                var result = new FieldContainer();
                var modifiers = current.getModifiers();

                result.base = current;
                result.strType = type.getRoot(false);
                result.strTypeAnnotations = type.getRoot(true);
                result.rootType = type.fullRoot;
                result.strName = current.getSimpleName().toString();
                result.strAccess = current.getModifiers().stream().map(e -> e.toString().toLowerCase(Locale.ROOT)).collect(Collectors.joining(" "));
                result.annotations = type.getAnnotations();

                fields.put(result.strName, result);

                bag.imports.addAll(type.getImports());

                if(currentType instanceof TypeElement) {
                    var currentTypeElement = (TypeElement) currentType;
                    var setterType = currentTypeElement.getSimpleName().toString();

                    result.hasSuperGetter = APUtils.hasOverride(typeUtils, methods, "get" + APUtils.camelCase(result.strName), setterType);
                    result.hasSuperSetter = APUtils.hasOverride(typeUtils, methods, "set" + APUtils.camelCase(result.strName), "void", setterType);

                    if(result.hasSuperGetter || result.hasSuperSetter) {
                        bag.imports.add("java.lang.Override");
                    }
                }

                bag.fields.add(result);

                if(modifiers.contains(Modifier.PUBLIC)) {
                    throw new Exception("DTO field cannot be public. [" + bag.clazz.getSimpleName() + "]");
                }
            }
        }

        if(bag.baseClass != null) {
            var type = typeUtils.asElement(bag.baseClass.asType());

            if(type instanceof TypeElement) {
                var typeElement = (TypeElement) type;

                if(!typeElement.getSimpleName().toString().equals("Object")) {
                    bag.imports.add(typeElement.getQualifiedName().toString());
                }
            }
        }

        for(var constructor: APUtils.getTypeMirrorFromAnnotationValue(annotation::value)) {
            var computed = createConstructorFor(
                (TypeElement) typeUtils.asElement(constructor),
                fields,
                bag.newName,
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
        var file = filter.createSourceFile(bag.pkg.getQualifiedName() + "." + bag.newName);

        try (var w = file.openWriter()) {
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

            pw.print("public class " + bag.newName);

            if(bag.clazz instanceof TypeElement) {
                var tClazz = (TypeElement) bag.clazz;
                var modifiers = tClazz.getModifiers();
                var constructors = ElementFilter.constructorsIn(tClazz.getEnclosedElements());

                if(bag.clazz == null) {
                    throw new Exception("No executable class . [" + bag.newName + "]");
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
                        pw.println("\t" + field.strAccess + " Optional<" + field.strTypeAnnotations + "> " + field.strName + ";");
                    } else {
                        pw.println("\t" + field.strAccess + " " + field.strTypeAnnotations + " " + field.strName + ";");
                    }
                }

                pw.print("\n");
            }

            pw.println("\tpublic " + bag.newName + "() {");

            if(bag.clazz instanceof TypeElement) {
                pw.println("\t\tsuper();\n");
            }

            for(var field: bag.fields) {
                var constant = field.base.getConstantValue();

                pw.print("\t\tthis.");
                pw.print(field.strName);
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
                    pw.println("\tpublic Boolean has" + APUtils.camelCase(field.strName) + "Field() {");
                    pw.println("\t\treturn this." + field.strName + " != null ? true : false;");
                    pw.println("\t}");
                }

                pw.print("\n");

                pw.println("\tpublic " + field.strTypeAnnotations + " get" + APUtils.camelCase(field.strName) + "(" + field.strType + " def) {");

                if(serializer_enabled) {
                    pw.println("\t\treturn this." + field.strName + " != null ? this." + field.strName + ".orElse(def) : def;");
                } else {
                    pw.println("\t\treturn this." + field.strName + " != null ? this." + field.strName + " : def;");
                }

                pw.println("\t}");
                pw.print("\n");

                if(field.hasSuperGetter) {
                    pw.println("\t@Override");
                }

                pw.println("\tpublic " + field.strTypeAnnotations + " get" + APUtils.camelCase(field.strName) + "() {");
                pw.println("\t\treturn this.get" + APUtils.camelCase(field.strName) + "(null);");
                pw.println("\t}");
                pw.print("\n");

                if(!field.base.getModifiers().contains(Modifier.FINAL)) {
                    pw.println("\tpublic void clear" + APUtils.camelCase(field.strName) + "() {");
                    pw.print("\t\tthis." + field.strName + " = null;");

                    if(field.hasSuperSetter) {
                        pw.print(" super.set" + APUtils.camelCase(field.strName) + "(null);");
                    }

                    pw.println("\n\t}");
                    pw.print("\n");

                    if(field.hasSuperSetter) {
                        pw.println("\t@Override");
                    }

                    if(serializer_enabled) {
                        pw.println("\tpublic void set" + APUtils.camelCase(field.strName) + "(final " + field.strType + " value) {");
                        pw.print("\t\tthis." + field.strName + " = Optional.ofNullable(value);");

                        if(field.hasSuperSetter) {
                            pw.print(" super.set" + APUtils.camelCase(field.strName) + "(value);");
                        }

                        pw.println("\n\t}");
                    } else {
                        pw.println("\tpublic void set" + APUtils.camelCase(field.strName) + "(final " + field.strType + " value) {");
                        pw.print("\t\tthis." + field.strName + " = value;");

                        if(field.hasSuperSetter) {
                            pw.print(" super.set" + APUtils.camelCase(field.strName) + "(value);");
                        }

                        pw.println("\n\t}");
                    }
                }
            }

            pw.println("}");
            pw.flush();
        } catch (Exception x) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, x.toString());
        }
    }

    private void makeDefaultSerializer(DefaultDatasetClassBag bag, SettingsBag settings) throws IOException {
        var file = filter.createSourceFile(bag.pkg.getQualifiedName() + "." + bag.newName);

        try (var w = file.openWriter()) {
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

            pw.println("class " + bag.newName + " extends StdSerializer<" + from_name +"> {");
            pw.println("\tpublic " + bag.newName + "() {");
            pw.println("\t\tsuper(" + from_name + ".class);");
            pw.println("\t}");
            pw.print("\n");
            pw.println("\tpublic " + bag.newName + "(Class<" + from_name + "> from) {");
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
        String constructorName,
        List<String> parentImports,
        Element clazz,
        Boolean serializerEnabled
    ) {
        var bag = new ConstructorBag();
        var builder = new StringBuilder();
        var objectFields = ElementFilter.fieldsIn(element.getEnclosedElements()).stream().collect(Collectors.toMap(e -> e.getSimpleName().toString(), e -> e));
        var objectMethods = ElementFilter.methodsIn(element.getEnclosedElements()).stream().collect(Collectors.toMap(e -> e.getSimpleName().toString(), e -> e));
        var simpleName = element.getSimpleName().toString();
        var conversionName = element.getAnnotation(RequestDTO.class) != null ? getNewRequestClassName(simpleName) : simpleName;
        var objectFieldsSet = objectFields.values();
        var fulfilled = new HashSet<>();
        var skipCount = 0;

        builder.append("\tpublic ")
            .append(constructorName)
            .append("(")
            .append(conversionName)
            .append(" from)")
        .append(" {\n\t\tthis();\n\n\t\tif(from != null) {");

        //
        // Стандартный обработчик конверсии
        //
        for(var field: objectFieldsSet) {
            var localSimpleName = field.getSimpleName().toString();
            var getterSignature = APUtils.toGetter(localSimpleName);
            var getter = objectMethods.get(getterSignature);
            var mirrorSignature = fields.keySet().stream().filter(e -> e.startsWith(localSimpleName)).min(Comparator.comparingInt(String::length)).orElse("");
            var mirrorRest = mirrorSignature.length() > localSimpleName.length() ? mirrorSignature.substring(field.getSimpleName().length()) : "";
            var mirror = fields.get(mirrorSignature);
            var fieldModifiers = field.getModifiers();

            if(objectFields.containsKey(mirrorSignature) && !mirrorRest.isEmpty()) {
                continue;
            }

            if(fulfilled.contains(mirrorSignature)) {
                continue;
            } else {
                fulfilled.add(mirrorSignature);
            }

            //
            // Условия обработки поля
            //
            if(
                mirror != null && !mirror.base.getModifiers().contains(Modifier.FINAL) && (
                    getter != null && getter.getModifiers().contains(Modifier.PUBLIC) ||
                    element.getAnnotation(RequestDTO.class) != null && (
                        fieldModifiers.contains(Modifier.PRIVATE) ||
                        fieldModifiers.contains(Modifier.PROTECTED)
                    )
                )
            ) {
                //
                // Пропустить проверку на null в этом поле.
                //
                var skip_null_check = field.getAnnotation(SkipNullCheck.class) != null || mirror.base.getAnnotation(SkipNullCheck.class) != null;
                //
                // Возвращаемый тип
                //
                var return_type = getter != null ? getter.getReturnType() : field.asType();

                //
                // Если не найдено точное совпадение
                //
                if(mirrorRest.length() != 0) {
                    //
                    // _id => id
                    // Id => id
                    //
                    if(mirrorRest.length() > 1) {
                        mirrorRest = mirrorRest.contains("_") ? mirrorRest.substring(1) : mirrorRest;
                        mirrorRest = mirrorRest.substring(0, 1).toLowerCase(Locale.ROOT) + mirrorRest.substring(1);
                    }

                    if(return_type.getKind() != TypeKind.VOID) {
                        var return_type_element = typeUtils.asElement(return_type);

                        if (return_type_element instanceof TypeElement) {
                            var type = (TypeElement) return_type_element;
                            var rest_getter = APUtils.toGetter(mirrorRest);
                            var get_rest = ElementFilter.methodsIn(type.getEnclosedElements()).stream().filter(e -> e.getSimpleName().toString().equals(rest_getter)).findFirst().orElse(null);

                            if (getter == null || getter.getParameters().size() == 0 && get_rest != null && ((TypeElement) typeUtils.asElement(get_rest.getReturnType())).getQualifiedName().toString().equals(mirror.rootType)) {
                                builder
                                    .append("\n\t\t\tthis.set")
                                    .append(APUtils.camelCase(mirror.strName))
                                    .append("(Objects.requireNonNullElse(from.")
                                    .append(getterSignature)
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
                            if(type_return_element.getQualifiedName().toString().equals(mirror.rootType)) {
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
                                                case "java.util.LinkedHashSet", "java.util.HashSet", "java.util.EnumSet", "java.util.TreeSet", "java.util.Set" -> builder
                                                        .append(skip_null_check ? "" : "\n\t\t\tif(from.")
                                                        .append(skip_null_check ? "" : APUtils.toGetter(mirror.strName))
                                                        .append(skip_null_check ? "" : "() != null) {")
                                                        .append("\n\t\t\t")
                                                        .append(skip_null_check ? "" : "\t")
                                                        .append("this.")
                                                        .append(APUtils.toSetter(mirror.strName))
                                                        .append("((")
                                                        .append(mirror.strType)
                                                        .append(") from.")
                                                        .append(getterSignature)
                                                        .append("().clone());")
                                                    .append(skip_null_check ? "" : "\n\t\t\t}");
                                                case "java.util.LinkedList", "java.util.ArrayList", "java.util.List" -> {
                                                    builder
                                                        .append(skip_null_check ? "" : "\n\t\t\tif(from.")
                                                        .append(skip_null_check ? "" : APUtils.toGetter(mirror.strName))
                                                        .append(skip_null_check ? "" : "() != null) {")
                                                        .append("\n\t\t\t")
                                                        .append(skip_null_check ? "" : "\t")
                                                        .append("this.")
                                                        .append(APUtils.toSetter(mirror.strName))
                                                        .append("(new ArrayList<>(from.")
                                                        .append(getterSignature)
                                                        .append("()));")
                                                    .append(skip_null_check ? "" : "\n\t\t\t}");

                                                    bag.imports.add("java.util.ArrayList");
                                                }
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
                                            var response_dto_annotation = mirror_declared_type_element.getAnnotation(ResponseDTO.class);

                                            //
                                            // Если был найден подходящий конструктор или целевого типа есть аннотация responseDTO
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
                                                    .append(skip_null_check ? "" : APUtils.toGetter(mirror.strName))
                                                    .append(skip_null_check ? "" : "() != null) {")
                                                    .append("\n\t\t\t")
                                                    .append(skip_null_check ? "" : "\t")
                                                    .append("this.")
                                                    .append(APUtils.toSetter(mirror.strName))
                                                    .append("(from.")
                                                    .append(APUtils.toGetter(mirror.strName))
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
                                                skipCount++;
                                            }
                                        }
                                    } else {
                                        skipCount++;
                                    }
                                } else {
                                    builder.append("\n\t\t\tthis.").append(APUtils.toSetter(mirror.strName)).append("(from.").append(getterSignature).append("());");
                                }
                            } else {
                                //
                                // Беру тип, который нужен, и ищу у него конструктор с типом который есть.
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
                                            .append(skip_null_check ? "" : "\n\t\t\tif(from.")
                                            .append(skip_null_check ? "" : APUtils.toGetter(mirror.strName))
                                            .append(skip_null_check ? "" : "() != null) {")
                                            .append("\n\t\t\t")
                                            .append(skip_null_check ? "" : "\t")
                                            .append("this.")
                                            .append(APUtils.toSetter(mirror.strName))
                                            .append("(new ")
                                            .append(request_dto_annotation != null ? getNewClassName(name) : name)
                                            .append("(from.")
                                            .append(getterSignature)
                                            .append("()));")
                                        .append(skip_null_check ? "" : "\n\t\t\t}");

                                        bag.imports.add(t_mirror_type.getQualifiedName().toString());
                                    } else {
                                        skipCount++;
                                    }
                                } else {
                                    skipCount++;
                                }
                            }
                        } else {
                            skipCount++;
                        }
                    } else {
                        skipCount++;
                    }
                } else {
                    skipCount++;
                }
            } else {
                skipCount++;
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
                            builder.append("\n\t\t\t").append(replaceAllLinks(line, param_name, serializerEnabled, clazz.getSimpleName().toString(), constructorName));
                        }

                        bag.imports.addAll(getIntersectOf(constructor, parentImports));
                    }
                }
            }
        }

        if(skipCount > 0) {
            System.out.println("[WARN] When creating the converter " + constructorName + " -> " + conversionName + " " + skipCount + " fields were omitted");
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
