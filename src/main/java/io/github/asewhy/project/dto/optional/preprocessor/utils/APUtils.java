package io.github.asewhy.project.dto.optional.preprocessor.utils;

import io.github.asewhy.project.dto.optional.preprocessor.enums.FieldPolicy;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.MirroredTypesException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

    public static String toSnakeCase(String input) {
        return input.replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2").replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    public static String toSnakeUpperCase(String input) {
        return input.replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2").replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    public static String toSnakeLowerCase(String input) {
        return input.replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2").replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
    }

    public static String toKebabCase(String input) {
        return input.replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2").replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
    }

    public static String toKebabLowerCase(String input) {
        return input.replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2").replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
    }

    public static String toKebabUpperCase(String input) {
        return input.replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2").replaceAll("([a-z])([A-Z])", "$1-$2").toUpperCase(Locale.ROOT);
    }

    public static String convertToCurrentCase(String input, FieldPolicy policy) {
        if(policy == null) {
            return toSnakeCase(input);
        }

        return switch (policy) {
            case CamelCase -> Pattern.compile("(?:_|-)([a-z])").matcher(input).replaceAll(m -> m.group(1).toUpperCase());
            case SnakeCase -> toSnakeCase(input);
            case LowerSnakeCase -> toSnakeLowerCase(input);
            case UpperSnakeCase -> toSnakeUpperCase(input);
            case KebabCase -> toKebabCase(input);
            case LowerKebabCase -> toKebabLowerCase(input);
            case UpperKebabCase -> toKebabUpperCase(input);
            case None -> input;
        };
    }

    public static String toGetter(String field_name) {
        return "get" + camelCase(field_name);
    }

    public static String toCurrentPolicyGetter(String field_name, FieldPolicy policy) {
        return "get" + convertToCurrentCase(field_name, policy);
    }

    public static String toSetter(String field_name) {
        return "set" + camelCase(field_name);
    }

    public static String toCurrentPolicySetter(String field_name, FieldPolicy policy) {
        return "set" + convertToCurrentCase(field_name, policy);
    }

    public static boolean hasOverride(Types typeUtils, List<ExecutableElement> method, String name, String type, String... args) {
        return method.stream().anyMatch(e -> {
            var result = e.getSimpleName().contentEquals(name);
            var returnTypeKind = e.getReturnType();
            var returnType = typeUtils.asElement(returnTypeKind);

            if(returnType instanceof TypeElement) {
                var returnTypeElement = (TypeElement) returnType;
                var returnTypeElementArguments =  e.getParameters();

                if(result) {
                    if(returnTypeKind.getKind() != TypeKind.VOID) {
                        result = returnTypeElement.getSimpleName().contentEquals(type);
                    } else {
                        result = "void".equals(type);
                    }
                }

                if(result && args.length > 0) {
                    var arguments = returnTypeElementArguments.stream().map(a -> a.getSimpleName().toString()).collect(Collectors.toList());

                    if(arguments.size() != args.length ) {
                        result = false;
                    } else {
                        for(var i = 0; i < args.length; i++) {
                            if(!arguments.get(i).equals(args[i])) {
                                return false;
                            }
                        }
                    }
                }
            }

            return result;
        });
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
            var invocationHandler = Proxy.getInvocationHandler(processingEnv);

            try {
                var field = invocationHandler.getClass().getDeclaredField("val$delegateTo");

                field.setAccessible(true);

                var o = field.get(invocationHandler);

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