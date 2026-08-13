package javapi.params;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import javapi.annotations.file;
import javapi.annotations.form;
import javapi.annotations.value;
import javapi.request.Request;

class FormFileValueBindingTest {

    static class Targets {

        public String form(@form("name") String name, @form("age") int age) {
            return name + ":" + age;
        }

        public String optionalForm(@form("nick") @javapi.annotations.optional String nick) {
            return nick == null ? "none" : nick;
        }

        public String upload(@form("note") String note, @file("document") UploadedFile file) {
            return note + "|" + file.filename() + "|" + new String(file.content(), StandardCharsets.UTF_8);
        }

        public String optionalUpload(@file("document") @javapi.annotations.optional UploadedFile file) {
            return file == null ? "none" : file.filename();
        }

        public String value(@value("feature.flag") boolean flag, @value("feature.name") String name) {
            return flag + ":" + name;
        }
    }

    private static Method find(String name) {
        for (Method method : Targets.class.getMethods()) {
            if (method.getName().equals(name)) {
                return method;
            }
        }
        throw new AssertionError("no method " + name);
    }

    private static Object[] bind(String methodName, Request request) {
        return new ParamBinder(find(methodName)).bind(request);
    }

    private static Request formRequest(Map<String, String> form) {
        return Request.builder()
                .method("POST")
                .path("/upload")
                .form(form)
                .build();
    }

    @Test
    void bindsFormFieldsWithCoercion() {
        Object[] args = bind("form", formRequest(Map.of("name", "bolt", "age", "3")));
        assertEquals("bolt", args[0]);
        assertEquals(3, args[1]);
    }

    @Test
    void missingMandatoryFormFieldFails() {
        RequestValidationError error = assertThrows(RequestValidationError.class,
                () -> bind("form", formRequest(Map.of("name", "bolt"))));
        assertEquals("missing", error.errors().get(0).type());
        assertEquals(List.of("form", "age"), error.errors().get(0).loc());
    }

    @Test
    void optionalFormFieldBindsNull() {
        Object[] args = bind("optionalForm", formRequest(Map.of()));
        assertEquals(null, args[0]);
    }

    @Test
    void bindsFileUpload() {
        Request request = Request.builder()
                .method("POST")
                .path("/upload")
                .form(Map.of("note", "hi"))
                .files(List.of(new UploadedFile("document", "report.txt", "text/plain",
                        "contents".getBytes(StandardCharsets.UTF_8))))
                .build();
        Object[] args = bind("upload", request);
        assertEquals("hi", args[0]);
        UploadedFile file = (UploadedFile) args[1];
        assertEquals("document", file.name());
        assertEquals("report.txt", file.filename());
        assertEquals("text/plain", file.contentType());
        assertEquals("contents", new String(file.content(), StandardCharsets.UTF_8));
    }

    @Test
    void missingMandatoryFileFails() {
        RequestValidationError error = assertThrows(RequestValidationError.class,
                () -> bind("upload", formRequest(Map.of("note", "hi"))));
        assertEquals("missing", error.errors().get(0).type());
        assertTrue(error.errors().get(0).loc().contains("document"));
    }

    @Test
    void optionalFileBindsNull() {
        Object[] args = bind("optionalUpload", formRequest(Map.of()));
        assertEquals(null, args[0]);
    }

    @Test
    void valueInjectionFromSystemProperty() {
        System.setProperty("javapi.feature.flag", "true");
        System.setProperty("javapi.feature.name", "magic");
        try {
            Object[] args = bind("value", formRequest(Map.of()));
            assertEquals(true, args[0]);
            assertEquals("magic", args[1]);
        } finally {
            System.clearProperty("javapi.feature.flag");
            System.clearProperty("javapi.feature.name");
        }
    }

    @Test
    void missingRequiredValueFails() {
        RequestValidationError error = assertThrows(RequestValidationError.class,
                () -> bind("value", formRequest(Map.of())));
        assertTrue(error.errors().stream().anyMatch(f -> f.type().equals("missing")));
    }
}
