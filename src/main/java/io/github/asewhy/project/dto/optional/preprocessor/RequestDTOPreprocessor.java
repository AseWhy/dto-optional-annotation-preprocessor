package io.github.asewhy.project.dto.optional.preprocessor;

import io.github.asewhy.project.dto.optional.preprocessor.annotations.RequestDTO;
import io.github.asewhy.project.dto.optional.preprocessor.members.*;
import io.github.asewhy.project.dto.optional.preprocessor.processors.DateFormatPreprocessor;
import io.github.asewhy.project.dto.optional.preprocessor.processors.base.BasePreprocessor;
import io.github.asewhy.project.dto.optional.preprocessor.utils.APUtils;

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
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.stream.Collectors;

@SupportedSourceVersion(SourceVersion.RELEASE_6)
@SupportedAnnotationTypes({ "io.github.asewhy.project.dto.optional.preprocessor.annotations.RequestDTO" })
public class RequestDTOPreprocessor extends AbstractProcessor {
    protected Types typeUtils;
    protected Elements elementUtils;
    protected Filer filter;
    protected List<Class<? extends BasePreprocessor<?>>> processors;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        var env = APUtils.unwrap(processingEnv);

        assert env != null;

        this.typeUtils = env.getTypeUtils();
        this.elementUtils = env.getElementUtils();
        this.filter = env.getFiler();
        this.processors = new ArrayList<>();
        this.processors.add(DateFormatPreprocessor.class);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (var clazz : roundEnv.getElementsAnnotatedWith(RequestDTO.class)) {
            if(clazz.getKind() != ElementKind.CLASS) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "The @RequestDTO annotation is only allowed as class annotation");
                break;
            }

            var annotation = clazz.getAnnotation(RequestDTO.class);

            try {
                makeDefaultRequestOf(clazz, annotation);
            } catch (Exception x) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, x.toString());
            }
        }

        return true;
    }

    public void makeDefaultRequestOf(Element clazz, RequestDTO annotation) throws Exception {
        var bag = new DefaultDatasetClassBag();
        var settings = new SettingsBag();
        var typeCazz = (TypeElement) ((DeclaredType) clazz.asType()).asElement();
        var superClazz = typeCazz.getSuperclass();
        var clazzModifiers = clazz.getModifiers();
        var methods = ElementFilter.methodsIn(typeCazz.getEnclosedElements());

        bag.fields = new ArrayList<>();
        bag.newName = getNewClassName(clazz.getSimpleName().toString());
        bag.baseClass = superClazz.getKind() != TypeKind.NONE ? typeUtils.asElement(superClazz) : null;
        bag.imports = new ArrayList<>(List.of("java.util.Optional"));
        bag.clazz = clazz;
        bag.pkg = elementUtils.getPackageOf(clazz);

        settings.policy = annotation.policy();
        settings.createBag = annotation.createBag();

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

                result.rootType = type.fullRoot;
                result.strType = type.getRoot(false);
                result.strTypeAnnotations = type.getRoot(true);
                result.strName = current.getSimpleName().toString();
                result.strAccess = modifiers.stream().map(e -> e.toString().toLowerCase(Locale.ROOT)).collect(Collectors.joining(" "));
                result.annotations = type.getAnnotations();
                result.base = current;

                bag.fields.add(result);
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

                if(modifiers.contains(Modifier.PUBLIC)) {
                    throw new Exception("DTO field cannot be public. [" + bag.clazz.getSimpleName() + "]");
                }
            }

            bag.imports.addAll(getFieldConversionImports(current));
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

        if(settings.createBag) {
            bag.imports.add("io.github.asewhy.project.dto.optional.preprocessor.runtime.PublicBag");
        }

        bag.imports.add("com.fasterxml.jackson.annotation.JsonProperty");

        makeDefaultRequestClass(bag, settings);
    }

    private void makeDefaultRequestClass(DefaultDatasetClassBag bag, SettingsBag settings) throws IOException {
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
            pw.println(" * Этот класс нельзя использовать как ответ сервера, из-за того что Optional не дружит с маппером, т.к. не реализует serializable");
            pw.println(" * Для ответа сервера следует отметить целевой класс аннотацией @ResponseDTO и использовать TargetClassName + ResponseDTO");
            pw.println(" * Это реализация Data Transfer Object для запроса. Реализованно от класса @see {@link " + bag.clazz.getSimpleName() + "}");
            pw.println(" */");

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
                    pw.println("\t" + field.strAccess + " Optional<" + field.strTypeAnnotations + "> " + field.strName + ";");
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
                pw.print((constant == null ? null : "Optional.ofNullable(" + (constant instanceof String ? "\"" + constant + "\"" : constant) + ")"));
                pw.println(";");
            }

            pw.println("\t}");

            for(var field: bag.fields) {
                pw.print("\n");
                pw.println("\tpublic Boolean has" + APUtils.camelCase(field.strName) + "Field() {");
                pw.println("\t\treturn this." + field.strName + " != null ? true : false;");
                pw.println("\t}");
                pw.print("\n");
                pw.println("\tpublic " + field.strTypeAnnotations + " get" + APUtils.camelCase(field.strName) + "(" + field.strType + " def) {");
                pw.println("\t\treturn this." + field.strName + " != null ? this." + field.strName + ".orElse(def) : def;");
                pw.println("\t}");
                pw.print("\n");

                if(field.hasSuperGetter) {
                    pw.println("\t@Override");
                }

                pw.println("\tpublic " + field.strTypeAnnotations + " get" + APUtils.camelCase(field.strName) + "() {");
                pw.println("\t\treturn this." + APUtils.toGetter(field.strName) + "(null);");
                pw.println("\t}\n");

                if(!field.base.getModifiers().contains(Modifier.FINAL)) {
                    try {
                        writeSetter(field, pw, settings);
                    } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
            }

            if(settings.createBag) {
                pw.println("\n\tpublic PublicBag toBag() {");
                pw.println("\t\tvar bag = new PublicBag();\n");

                for (var field : bag.fields) {
                    pw.println("\t\tbag.set(\"" + field.strName + "\", " + field.strName + ");");
                }

                pw.println("\n\t\treturn bag;");
                pw.println("\t}");
            }

            pw.println("}");
            pw.flush();
        } catch (Exception x) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, x.toString());
        }
    }

    private String getNewClassName(String input) {
        return input.endsWith("DTO") ? input.substring(0, input.length() - 3) + "RequestDTO" : input + "RequestDTO";
    }

    private GenericBag getGenerics(TypeMirror from) {
        var root = typeUtils.asElement(from);

        if(root instanceof TypeElement) {
            var type = (TypeElement) root;
            var qualified = type.getQualifiedName().toString();
            var type_annotation = type.getAnnotation(RequestDTO.class);
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

                bag.annotations.addAll(
                    declared.getAnnotationMirrors().stream().map(this::handleAnnotation).collect(Collectors.toList())
                );
            }

            return bag;
        } else {
            return null;
        }
    }

    private void writeSetter(
        FieldContainer field,
        PrintWriter pw,
        SettingsBag settings
    ) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        var write_anyone = false;

        for(var processor: processors) {
            if(processor.getConstructor(PrintWriter.class).newInstance(pw).process(field.base, field, true, settings)) {
                write_anyone = true;
            }
        }

        if(!write_anyone) {
            if(field.hasSuperSetter) {
                pw.println("\t@Override");
            }

            pw.println("\t@JsonProperty(\"" + APUtils.convertToCurrentCase(field.strName, settings.policy) + "\")");
            pw.println("\tpublic void " + APUtils.toSetter(field.strName) + "(" + field.strType + " value) {");
            pw.println("\t\tthis." + field.strName + " = Optional.ofNullable(value);");

            if(field.hasSuperSetter) {
                pw.println("\t\tsuper.set" + APUtils.camelCase(field.strName) + "(value);");
            }

            pw.println("\t}");
        }
    }

    private List<String> getFieldConversionImports(Element current){
        var imports = new ArrayList<String>();

        for(var processor_class: processors) {
            try {
                var processor = processor_class.getConstructor(PrintWriter.class).newInstance((PrintWriter) null);
                var p_imports = processor.getProvidedImports(current);

                if(p_imports != null) {
                    imports.addAll(p_imports);
                }
            } catch (InstantiationException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }

        return imports;
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
}
