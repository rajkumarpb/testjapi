package javapi.json;

import java.lang.reflect.Type;

public interface Codec {
    String write(Object value);

    Object read(String json, Type targetType);

    /**
     * Serialize straight into {@code out} without materializing an intermediate
     * string. Defaults to {@link #write(Object)}; fast codecs override it to
     * stream tokens into the sink directly.
     */
    default void writeTo(Object value, JsonOutput out) {
        out.append(write(value));
    }
}
