package com.astecom.project.dto.optional.preprocessor.utils;

import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.MirroredTypesException;
import javax.lang.model.type.TypeMirror;
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
}