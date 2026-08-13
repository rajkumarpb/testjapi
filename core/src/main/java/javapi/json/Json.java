package javapi.json;

import java.lang.reflect.Type;

public final class Json {

    private static volatile Codec codec = new DefaultCodec();

    private Json() {
    }

    public static void codec(Codec codec) {
        Json.codec = codec;
    }

    public static String write(Object value) {
        return codec.write(value);
    }

    public static void writeTo(Object value, JsonOutput out) {
        codec.writeTo(value, out);
    }

    public static Object read(String json, Type targetType) {
        return codec.read(json, targetType);
    }

    @SuppressWarnings("unchecked")
    public static <T> T parse(String json, Type targetType) {
        return (T) codec.read(json, targetType);
    }

    static String escape(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 16);
        appendEscaped(new StringBuilderJsonOutput(sb), value);
        return sb.toString();
    }

    static void appendEscaped(JsonOutput out, String value) {
        int runStart = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\' || c < 0x20) {
                if (i > runStart) {
                    out.append(value, runStart, i);
                }
                switch (c) {
                    case '"' -> out.append("\\\"");
                    case '\\' -> out.append("\\\\");
                    case '\b' -> out.append("\\b");
                    case '\f' -> out.append("\\f");
                    case '\n' -> out.append("\\n");
                    case '\r' -> out.append("\\r");
                    case '\t' -> out.append("\\t");
                    default -> {
                        out.append("\\u");
                        appendHex(out, c);
                    }
                }
                runStart = i + 1;
            }
        }
        if (runStart < value.length()) {
            out.append(value, runStart, value.length());
        }
    }

    private static void appendHex(JsonOutput out, int c) {
        String hex = "0123456789abcdef";
        out.append(hex.charAt((c >> 12) & 0xF));
        out.append(hex.charAt((c >> 8) & 0xF));
        out.append(hex.charAt((c >> 4) & 0xF));
        out.append(hex.charAt(c & 0xF));
    }

    static final class StringBuilderJsonOutput implements JsonOutput {
        private final StringBuilder out;

        StringBuilderJsonOutput(StringBuilder out) {
            this.out = out;
        }

        @Override
        public void append(String value) {
            out.append(value);
        }

        @Override
        public void append(String value, int start, int end) {
            out.append(value, start, end);
        }

        @Override
        public void append(char c) {
            out.append(c);
        }
    }
}
