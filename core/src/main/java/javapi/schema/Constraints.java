package javapi.schema;

import java.lang.reflect.AnnotatedElement;
import java.util.regex.Pattern;
import javapi.annotations.email;
import javapi.annotations.max;
import javapi.annotations.maxlength;
import javapi.annotations.min;
import javapi.annotations.minlength;
import javapi.annotations.optional;
import javapi.annotations.pattern;

public record Constraints(
        Integer minLength,
        Integer maxLength,
        Long min,
        Long max,
        Pattern pattern,
        boolean email,
        boolean optional) {

    public static final Constraints NONE = new Constraints(null, null, null, null, null, false, false);

    public static Constraints of(AnnotatedElement element) {
        Integer minLength = element.isAnnotationPresent(minlength.class)
                ? element.getAnnotation(minlength.class).value()
                : null;
        Integer maxLength = element.isAnnotationPresent(maxlength.class)
                ? element.getAnnotation(maxlength.class).value()
                : null;
        Long min = element.isAnnotationPresent(min.class)
                ? element.getAnnotation(min.class).value()
                : null;
        Long max = element.isAnnotationPresent(max.class)
                ? element.getAnnotation(max.class).value()
                : null;
        Pattern pattern = element.isAnnotationPresent(pattern.class)
                ? Pattern.compile(element.getAnnotation(pattern.class).value())
                : null;
        boolean email = element.isAnnotationPresent(email.class);
        boolean optional = element.isAnnotationPresent(optional.class);
        if (minLength == null && maxLength == null && min == null && max == null
                && pattern == null && !email && !optional) {
            return NONE;
        }
        return new Constraints(minLength, maxLength, min, max, pattern, email, optional);
    }

    public boolean none() {
        return this == NONE || (minLength == null && maxLength == null && min == null && max == null
                && pattern == null && !email && !optional);
    }
}
