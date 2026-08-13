package javapi.testkit;

import java.nio.charset.StandardCharsets;

/**
 * A file part for multipart requests issued through {@link TestClient}.
 */
public record TestFile(String filename, String contentType, byte[] content) {

    public static TestFile of(String filename, String contentType, String content) {
        return new TestFile(filename, contentType, content.getBytes(StandardCharsets.UTF_8));
    }
}
