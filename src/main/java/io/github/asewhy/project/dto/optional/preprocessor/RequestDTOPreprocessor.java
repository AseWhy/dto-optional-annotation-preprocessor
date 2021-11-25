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
        var classElement = ((TypeElement) ((DeclaredType) clazz.asType()).asElement());
        var superClazz = classElement.getSuperclass();

        bag.fields = new ArrayList<>();
        bag.new_name = getNewClassName(clazz.getSimpleName().toString());
        bag.base_class = superClazz.getKind() != TypeKind.NONE ? typeUtils.asElement(superClazz) : null;
        bag.imports = new ArrayList<>(List.of("java.util.Optional"));
        bag.clazz = clazz;
        bag.pkg = elementUtils.getPackageOf(clazz);

        settings.policy = annotation.policy();

        for(var current: ElementFilter.fieldsIn(clazz.getEnclosedElements())) {
            var type = getGenerics(current.asType());

            if (type != null) {
                var result = new FieldContainer();

                result.root_type = type.fullRoot;
                result.str_type = type.getRoot(false);
                result.str_type_annotations = type.getRoot(true);
                result.str_name = current.getSimpleName().toString();
                result.str_access = current.getModifiers().stream().map(e -> e.toString().toLowerCase(Locale.ROOT)).collect(Collectors.joining(" "));
                result.annotations = type.getAnnotations();
                result.base = current;

                bag.fields.add(result);
                bag.imports.addAll(type.getImports());
            }

            bag.imports.addAll(getFieldConversionImports(current));
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

        bag.imports.add("io.github.asewhy.project.dto.optional.preprocessor.runtime.PublicBag");
        bag.imports.add("com.fasterxml.jackson.annotation.JsonProperty");

        makeDefaultRequestClass(bag, settings);
    }

    private void makeDefaultRequestClass(DefaultDatasetClassBag bag, SettingsBag settings) throws IOException {
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
            pw.println(" * Сгенерировано автоматически с помощью dto-optional-annotation-preprocessor");
            pw.println(" * Этот класс нельзя использовать как ответ сервера, из-за того что Optional не дружит с маппером, т.к. не реализует serializable");
            pw.println(" * Для ответа сервера следует отметить целевой класс аннотацией @ResponseDTO и использовать TargetClassName + ResponseDTO");
            pw.println(" * Это реализация Data Transfer Object для запроса. Реализованно от класса @see {@link " + bag.clazz.getSimpleName() + "}");
            pw.println(" */");

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
                    pw.println("\t" + field.str_access + " Optional<" + field.str_type_annotations + "> " + field.str_name + ";");
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
                pw.print((constant == null ? null : "Optional.ofNullable(" + (constant instanceof String ? "\"" + constant + "\"" : constant) + ")"));
                pw.println(";");
            }

            pw.println("\t}");

            for(var field: bag.fields) {
                pw.print("\n");
                pw.println("\tpublic Boolean has" + APUtils.camelCase(field.str_name) + "Field() {");
                pw.println("\t\treturn this." + field.str_name + " != null ? true : false;");
                pw.println("\t}");
                pw.print("\n");
                pw.println("\tpublic " + field.str_type_annotations + " get" + APUtils.camelCase(field.str_name) + "(" + field.str_type + " def) {");
                pw.println("\t\treturn this." + field.str_name + " != null ? this." + field.str_name + ".orElse(def) : def;");
                pw.println("\t}");
                pw.print("\n");
                pw.println("\tpublic " + field.str_type_annotations + " get" + APUtils.camelCase(field.str_name) + "() {");
                pw.println("\t\treturn this." + APUtils.toGetter(field.str_name) + "(null);");
                pw.println("\t}\n");

                if(!field.base.getModifiers().contains(Modifier.FINAL)) {
                    try {
                        writeSetter(field.base, field, pw, settings);
                    } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
            }

            pw.println("\n\tpublic PublicBag toBag() {");
            pw.println("\t\tvar bag = new PublicBag();\n");

            for(var field: bag.fields) {
                pw.println("\t\tbag.set(\"" + field.str_name + "\", " + field.str_name + ");");
            }

            pw.println("\n\t\treturn bag;");
            pw.println("\t}");

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
        Element field_element,
        FieldContainer field,
        PrintWriter pw,
        SettingsBag settings
    ) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        var write_anyone = false;

        for(var processor: processors) {
            if(processor.getConstructor(PrintWriter.class).newInstance(pw).process(field_element, field, true, settings)) {
                write_anyone = true;
            }
        }

        if(!write_anyone) {
            pw.println("\t@JsonProperty(\"" + APUtils.convertToCurrentCase(field.str_name, settings.policy) + "\")");
            pw.println("\tpublic void " + APUtils.toSetter(field.str_name) + "(" + field.str_type + " value) {");
            pw.println("\t\tthis." + field.str_name + " = Optional.ofNullable(value);");
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
