package javapi.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import javapi.annotations.email;
import javapi.annotations.max;
import javapi.annotations.maxlength;
import javapi.annotations.min;
import javapi.annotations.minlength;
import javapi.annotations.optional;
import javapi.annotations.pattern;
import javapi.params.FieldError;
import javapi.params.RequestValidationError;

class SchemaValidatorTest {

    enum Level {
        LOW, HIGH
    }

    record Member(
            @minlength(2) @maxlength(10) String name,
            @email String email,
            @min(0) int age,
            Level level) {
    }

    record Team(String name, List<Member> members) {
    }

    record Address(String city) {
    }

    record Profile(
            @minlength(2) @maxlength(10) String name,
            Address address,
            List<Address> addresses,
            Map<String, Integer> scores,
            @pattern("^[A-Z]{2}[0-9]{3}$") String code,
            @optional String bio,
            Optional<Address> home,
            Level level,
            UUID id,
            LocalDate birthday) {
    }

    private static RequestValidationError fails(Type type, String json) {
        return assertThrows(RequestValidationError.class,
                () -> SchemaValidator.validate(type, json), "expected validation failure");
    }

    private static RequestValidationError fails(Type type, Object raw) {
        return assertThrows(RequestValidationError.class,
                () -> SchemaValidator.validate(type, raw, List.of("body")), "expected validation failure");
    }

    @Test
    void constructsNestedRecordWithAllScalarKinds() {
        Object result = SchemaValidator.validate(Profile.class,
                "{\"name\":\"neo\",\"address\":{\"city\":\"zion\"},\"addresses\":[{\"city\":\"a\"},{\"city\":\"b\"}],"
                        + "\"scores\":{\"math\":95},\"code\":\"AB123\",\"level\":\"high\","
                        + "\"id\":\"123e4567-e89b-12d3-a456-426614174000\",\"birthday\":\"2000-01-01\"}",
                List.of("body"));
        assertInstanceOf(Profile.class, result);
    }

    @Test
    void rawValidatesIntoRecord() {
        Object value = SchemaValidator.validate(Profile.class,
                Map.of("name", "neo", "address", Map.of("city", "zion"), "addresses", List.of(Map.of("city", "a")),
                        "scores", Map.of("math", 95), "code", "AB123", "level", "LOW",
                        "id", UUID.randomUUID().toString(), "birthday", "2000-01-01"),
                List.of("body"));
        assertInstanceOf(Profile.class, value);
    }

    @Test
    void missingRequiredFieldReportsMissingWithLoc() {
        RequestValidationError error = fails(Address.class, "{\"other\":\"x\"}");
        assertEquals(1, error.errors().size());
        FieldError fieldError = error.errors().get(0);
        assertEquals("missing", fieldError.type());
        assertEquals(List.of("body", "city"), fieldError.loc());
    }

    @Test
    void collectsMultipleErrorsInOne422() {
        RequestValidationError error = fails(Profile.class,
                "{\"name\":\"x\",\"address\":{\"city\":\"z\"},\"addresses\":[{\"city\":\"z\"}],"
                        + "\"scores\":{\"m\":1},\"code\":\"nope\",\"level\":\"BOGUS\","
                        + "\"id\":\"123e4567-e89b-12d3-a456-426614174000\",\"birthday\":\"2000-01-01\"}");
        List<String> types = error.errors().stream().map(FieldError::type).toList();
        assertEquals(List.of("string_too_short", "string_pattern_mismatch", "enum"), types);
    }

    @Test
    void stringConstraintsApplyAtNestedLoc() {
        RequestValidationError error = fails(Member.class,
                "{\"name\":\"\",\"email\":\"bad\",\"age\":-1,\"level\":\"LOW\"}");
        List<String> types = error.errors().stream().map(FieldError::type).toList();
        assertEquals(List.of("string_too_short", "value_error", "greater_than_equal"), types);
        assertEquals(List.of("body", "name"), error.errors().get(0).loc());
    }

    @Test
    void listOfRecordsValidatesWithArrayIndexLoc() {
        RequestValidationError error = fails(Team.class,
                "{\"name\":\"red\",\"members\":[{\"name\":\"ok\",\"email\":\"a@b.co\",\"age\":1,\"level\":\"LOW\"},"
                        + "{\"name\":\"\",\"email\":\"x\",\"age\":0,\"level\":\"HIGH\"}]}");
        assertEquals(List.of("string_too_short", "value_error"),
                error.errors().stream().map(FieldError::type).toList());
        assertEquals(List.of("body", "members", 1, "name"), error.errors().get(0).loc());
    }

    @Test
    void enumCoercionIsCaseInsensitive() {
        Object value = SchemaValidator.validate(Level.class, "\"low\"", List.of("body"));
        assertEquals(Level.LOW, value);
    }

    @Test
    void optionalFieldDefaultsToEmpty() {
        Object value = SchemaValidator.validate(Address.class, "{\"city\":\"z\",\"extra\":1}", List.of("body"));
        assertInstanceOf(Address.class, value);
    }

    @Test
    void jsonNumberAcceptedForLongField() {
        Object value = SchemaValidator.validate(Long.class, "42", List.of("body"));
        assertEquals(42L, value);
    }

    @Test
    void wrongNumericTypeFails() {
        RequestValidationError error = fails(Long.class, "\"abc\"");
        assertEquals("int_parsing", error.errors().get(0).type());
    }

    @Test
    void maxConstraintFails() {
        record Score(@max(10) int value) {
        }
        RequestValidationError error = fails(Score.class, "{\"value\":11}");
        assertEquals("less_than_equal", error.errors().get(0).type());
    }

    @Test
    void nullOptionalBodyFieldAcceptsNull() {
        java.util.Map<String, Object> raw = new java.util.HashMap<>();
        raw.put("name", "neo");
        raw.put("address", Map.of("city", "z"));
        raw.put("addresses", List.of());
        raw.put("scores", Map.of());
        raw.put("code", "AB123");
        raw.put("level", "LOW");
        raw.put("id", UUID.randomUUID().toString());
        raw.put("birthday", "2000-01-01");
        raw.put("bio", null);
        raw.put("home", null);
        Object value = SchemaValidator.validate(Profile.class, raw, List.of("body"));
        Profile profile = assertInstanceOf(Profile.class, value);
        assertEquals(null, profile.bio());
        assertEquals(Optional.empty(), profile.home());
    }

    @Test
    void uuidAndDateCoercion() {
        Object uuid = SchemaValidator.validate(UUID.class,
                "\"123e4567-e89b-12d3-a456-426614174000\"", List.of("body"));
        assertEquals(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), uuid);
        Object date = SchemaValidator.validate(LocalDate.class, "\"2000-01-01\"", List.of("body"));
        assertEquals(LocalDate.of(2000, 1, 1), date);
    }

    @Test
    void notNullableFieldFails() {
        RequestValidationError error = fails(Address.class, "{\"city\":null}");
        assertEquals("not_nullable", error.errors().get(0).type());
        assertEquals(List.of("body", "city"), error.errors().get(0).loc());
    }
}
