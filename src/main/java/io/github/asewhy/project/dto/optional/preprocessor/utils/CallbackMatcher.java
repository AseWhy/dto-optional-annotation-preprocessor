package io.github.asewhy.project.dto.optional.preprocessor.utils;

import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

public class CallbackMatcher {
    private final Pattern pattern;

    public CallbackMatcher(String regex) {
        this.pattern = Pattern.compile(regex);
    }

    public String findMatches(String string, Function<MatchResult, String> callback) {
        final var matcher = this.pattern.matcher(string);
        final var builder = new StringBuilder();

        while(matcher.find()) {
            builder.append(callback.apply(matcher.toMatchResult()));
        }

        return builder.toString();
    }

    public static CallbackMatcher init(String pattern) {
        return new CallbackMatcher(pattern);
    }
}