package io.github.asewhy.project.dto.optional.preprocessor.utils;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.MirroredTypesException;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Стырил https://stackoverflow.com/questions/7687829/java-6-annotation-processing-getting-a-class-from-an-annotation
 */

public class APUtils {
    @FunctionalInterface
    public interface GetClassValue {
        void execute() throws MirroredTypeException, MirroredTypesException;
    }

    public static List<? extends TypeMirror> getTypeMirrorFromAnnotationValue(GetClassValue c) {
        try {
            c.execute();
        } catch(MirroredTypesException ex) {
            return ex.getTypeMirrors();
        }

        return List.of();
    }

    public static Boolean classExists(String full_name) {
        try {
            Class.forName(full_name);

            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static Boolean classesExists(String ...full_names) {
        return Arrays.stream(full_names).allMatch(APUtils::classExists);
    }

    public static String camelCase(String input) {
        return input.substring(0, 1).toUpperCase(Locale.ROOT) + input.substring(1);
    }

    public static String cameToSnakeCase(String input) {
        return input.replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2").replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    public static String toGetter(String field_name) {
        return "get" + camelCase(field_name);
    }

    public static String toSnakeSetter(String field_name) {
        return "set" + camelCase(cameToSnakeCase(field_name));
    }

    public static String toSetter(String field_name) {
        return "set" + camelCase(field_name);
    }

    /**
     * Спизжено: https://github.com/c0stra/fluent-api-end-check/pull/17/commits/5187d7716c71971456fbb45c10aafacebf048c85
     *
     * With the introduction of IntelliJ Idea 2020.3 release the ProcessingEnvironment
     * is not of type com.sun.tools.javac.processing.JavacProcessingEnvironment
     * but java.lang.reflect.Proxy.
     * The com.sun.source.util.Trees.instance() throws an IllegalArgumentException when the proxied processingEnv is passed.
     *
     * @param processingEnv possible proxied
     * @return ProcessingEnvironment unwrapped from the proxy if proxied or the original processingEnv
     */
    public static ProcessingEnvironment unwrap(ProcessingEnvironment processingEnv) {
        if (Proxy.isProxyClass(processingEnv.getClass())) {
            InvocationHandler invocationHandler = Proxy.getInvocationHandler(processingEnv);
            try {
                Field field = invocationHandler.getClass().getDeclaredField("val$delegateTo");
                field.setAccessible(true);
                Object o = field.get(invocationHandler);
                if (o instanceof ProcessingEnvironment) {
                    return (ProcessingEnvironment) o;
                } else {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "got "+o.getClass()+ " expected instanceof com.sun.tools.javac.processing.JavacProcessingEnvironment");
                    return null;
                }
            } catch (NoSuchFieldException | IllegalAccessException e) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage());
                return null;
            }
        } else {
            return processingEnv;
        }
    }
}