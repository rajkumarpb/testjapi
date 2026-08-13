package javapi.json;

/**
 * Char sink for JSON serialization. A codec writes tokens into this instead of
 * materializing an intermediate {@link String}, so a server can append JSON
 * directly into its response buffer (e.g. a Netty {@code ByteBuf}). Structural
 * characters are always ASCII; string content is handed over in raw runs so the
 * sink can UTF-8 encode surrogate pairs correctly.
 */
public interface JsonOutput {

    void append(String value);

    void append(String value, int start, int end);

    void append(char c);
}
