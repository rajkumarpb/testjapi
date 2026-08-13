package javapi.json.jackson;

import java.lang.reflect.Type;
import com.fasterxml.jackson.databind.ObjectMapper;
import javapi.json.Codec;
import javapi.json.JsonException;

public final class JacksonCodec implements Codec {

    private final ObjectMapper mapper;

    public JacksonCodec() {
        this(new ObjectMapper());
    }

    public JacksonCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new JsonException("Failed to serialize " + value.getClass().getName(), e);
        }
    }

    @Override
    public Object read(String json, Type targetType) {
        try {
            return mapper.readValue(json, mapper.getTypeFactory().constructType(targetType));
        } catch (Exception e) {
            throw new JsonException("Failed to parse JSON into " + targetType.getTypeName(), e);
        }
    }
}
