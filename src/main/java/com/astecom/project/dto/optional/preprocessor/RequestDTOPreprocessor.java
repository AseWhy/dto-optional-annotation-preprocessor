package com.astecom.project.dto.optional.preprocessor;

import com.astecom.project.dto.optional.preprocessor.annotations.RequestDTO;
import com.astecom.project.dto.optional.preprocessor.members.Annotation;
import com.astecom.project.dto.optional.preprocessor.members.DefaultDatasetClassBag;
import com.astecom.project.dto.optional.preprocessor.members.FieldContainer;
import com.astecom.project.dto.optional.preprocessor.members.GenericBag;
import com.astecom.project.dto.optional.preprocessor.processors.DateFormatPreprocessor;
import com.astecom.project.dto.optional.preprocessor.processors.base.BasePreprocessor;
import com.astecom.project.dto.optional.preprocessor.utils.APUtils;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@SupportedAnnotationTypes({ "com.astecom.project.dto.optional.preprocessor.annotations.RequestDTO" })
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public class RequestDTOPreprocessor extends AbstractProcessor {
    protected Types typeUtils;
    protected Elements elementUtils;
    protected Filer filter;
    protected List<Class<? extends BasePreprocessor<?>>> processors;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.typeUtils = processingEnv.getTypeUtils();
        this.elementUtils = processingEnv.getElementUtils();
        this.filter = processingEnv.getFiler();
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

            makeDefaultRequestOf(clazz);
        }

        return true;
    }

    public void makeDefaultRequestOf(Element clazz) {
        var bag = new DefaultDatasetClassBag();
        var superClazz = ((TypeElement) ((DeclaredType) clazz.asType()).asElement()).getSuperclass();

        bag.fields = new ArrayList<>();
        bag.new_name = getNewClassName(clazz.getSimpleName().toString());
        bag.base_class = superClazz.getKind() != TypeKind.NONE ? typeUtils.asElement(superClazz) : null;
        bag.imports = new ArrayList<>(List.of("java.util.Optional"));
        bag.clazz = clazz;
        bag.pkg = elementUtils.getPackageOf(clazz);

        for(var current: ElementFilter.fieldsIn(clazz.getEnclosedElements())) {
            var type = getGenerics(current.asType());

            if (type != null) {
                var result = new FieldContainer();

                result.root_type = type.fullRoot;
                result.str_type = type.getRoot();
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

        makeDefaultRequestClass(bag);
    }

    private void makeDefaultRequestClass(DefaultDatasetClassBag bag){
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
                pw.println(" * Этот класс нельзя использовать как ответ сервера, из-за того что Optional не дружит с маппером, т.к. не реализует serializable");
                pw.println(" * Для ответа сервера следует отметить целевой класс аннотацией @ResponseDTO и использовать TargetClassName + ResponseDTO");
                pw.println(" * Это реализация Data Transfer Object для запроса. Реализованно от класса @see {@link " + bag.clazz.getSimpleName() + "}");
                pw.println(" */");

                pw.print("public class " + bag.new_name);

                if(bag.base_class != null && !bag.base_class.getSimpleName().toString().equals("Object")) {
                    pw.print(" extends " + bag.base_class.getSimpleName());
                }

                pw.println(" {");

                if(bag.fields.size() > 0) {
                    for (var field : bag.fields) {
                        pw.println("\t" + field.str_access + " Optional<" + field.annotations + field.str_type + "> " + field.str_name + ";");
                    }

                    pw.print("\n");
                }

                pw.println("\tpublic " + bag.new_name + "() {");

                for(var field: bag.fields) {
                    pw.println("\t\tthis." + field.str_name + " = null;");
                }

                pw.println("\t}");

                for(var field: bag.fields) {
                    pw.print("\n");
                    pw.println("\tpublic Boolean has" + APUtils.camelCase(field.str_name) + "Field() {");
                    pw.println("\t\treturn this." + field.str_name + " != null ? true : false;");
                    pw.println("\t}");
                    pw.print("\n");
                    pw.println("\tpublic " + field.str_type + " get" + APUtils.camelCase(field.str_name) + "(" + field.str_type + " def) {");
                    pw.println("\t\treturn this." + field.str_name + " != null ? this." + field.str_name + ".orElse(def) : def;");
                    pw.println("\t}");
                    pw.print("\n");
                    pw.println("\tpublic " + field.str_type + " get" + APUtils.camelCase(field.str_name) + "() {");
                    pw.println("\t\treturn this.get" + APUtils.camelCase(field.str_name) + "(null);");
                    pw.println("\t}");
                    pw.print("\n");

                    try {
                        writeSetter(field.base, field, pw);
                    } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
                        e.printStackTrace();
                    }
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
        PrintWriter pw
    ) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        var write_anyone = false;

        for(var processor: processors) {
            if(processor.getConstructor(PrintWriter.class).newInstance(pw).process(field_element, field, true)) {
                write_anyone = true;
            }
        }

        if(!write_anyone) {
            pw.println("\tpublic void set" + APUtils.camelCase(field.str_name) + "(" + field.str_type + " value) {");
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
