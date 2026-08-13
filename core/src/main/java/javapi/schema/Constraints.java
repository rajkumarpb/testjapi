package javapi.schema;

import java.lang.reflect.AnnotatedElement;
import javapi.annotations.Email;
import javapi.annotations.Max;
import javapi.annotations.MaxLength;
import javapi.annotations.Min;
import javapi.annotations.MinLength;
import javapi.annotations.Optional;
import javapi.annotations.Pattern;

public record Constraints(
        Integer minLength,
        Integer maxLength,
        Long min,
        Long max,
        java.util.regex.Pattern pattern,
        boolean email,
        boolean optional) {

    public static final Constraints NONE = new Constraints(null, null, null, null, null, false, false);

    public static Constraints of(AnnotatedElement element) {
        Integer minLength = element.isAnnotationPresent(MinLength.class)
                ? element.getAnnotation(MinLength.class).value()
                : null;
        Integer maxLength = element.isAnnotationPresent(MaxLength.class)
                ? element.getAnnotation(MaxLength.class).value()
                : null;
        Long min = element.isAnnotationPresent(Min.class)
                ? element.getAnnotation(Min.class).value()
                : null;
        Long max = element.isAnnotationPresent(Max.class)
                ? element.getAnnotation(Max.class).value()
                : null;
        java.util.regex.Pattern pattern = element.isAnnotationPresent(Pattern.class)
                ? java.util.regex.Pattern.compile(element.getAnnotation(Pattern.class).value())
                : null;
        boolean email = element.isAnnotationPresent(Email.class);
        boolean optional = element.isAnnotationPresent(Optional.class);
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
