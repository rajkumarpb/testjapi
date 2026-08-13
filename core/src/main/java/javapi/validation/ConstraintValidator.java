package javapi.validation;

import java.util.regex.Pattern;
import javapi.schema.Constraints;

public final class ConstraintValidator {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private ConstraintValidator() {
    }

    public static ConstraintError check(Object value, Constraints constraints) {
        if (constraints == null || constraints.none() || value == null) {
            return null;
        }
        if (value instanceof String s) {
            if (constraints.minLength() != null && s.length() < constraints.minLength()) {
                return new ConstraintError("string_too_short",
                        "String should have at least " + constraints.minLength() + " characters");
            }
            if (constraints.maxLength() != null && s.length() > constraints.maxLength()) {
                return new ConstraintError("string_too_long",
                        "String should have at most " + constraints.maxLength() + " characters");
            }
            if (constraints.pattern() != null && !constraints.pattern().matcher(s).matches()) {
                return new ConstraintError("string_pattern_mismatch",
                        "String should match pattern '" + constraints.pattern() + "'");
            }
            if (constraints.email() && !EMAIL.matcher(s).matches()) {
                return new ConstraintError("value_error", "value is not a valid email address");
            }
        }
        if (value instanceof Number n) {
            if (constraints.min() != null && below(n, constraints.min())) {
                return new ConstraintError("greater_than_equal",
                        "Input should be greater than or equal to " + constraints.min());
            }
            if (constraints.max() != null && above(n, constraints.max())) {
                return new ConstraintError("less_than_equal",
                        "Input should be less than or equal to " + constraints.max());
            }
        }
        return null;
    }

    private static boolean below(Number n, long min) {
        return n instanceof Double d ? d < min : n instanceof Float f ? f < min : n.longValue() < min;
    }

    private static boolean above(Number n, long max) {
        return n instanceof Double d ? d > max : n instanceof Float f ? f > max : n.longValue() > max;
    }
}
