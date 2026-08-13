package javapi.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import javapi.annotations.Email;
import javapi.annotations.MaxLength;
import javapi.annotations.Min;
import javapi.annotations.MinLength;

class SchemaTest {

    record User(
            @MinLength(2) @MaxLength(20) @Email String name,
            @Min(1) int age,
            List<String> tags,
            Optional<String> nickname) {
    }

    enum Role {
        ADMIN, USER
    }

    @Test
    void mapsPrimitiveKinds() {
        assertEquals(Schema.Kind.INT, Schemas.schemaOf(int.class).kind());
        assertEquals(Schema.Kind.LONG, Schemas.schemaOf(Long.class).kind());
        assertEquals(Schema.Kind.STRING, Schemas.schemaOf(String.class).kind());
        assertEquals(Schema.Kind.BOOLEAN, Schemas.schemaOf(boolean.class).kind());
        assertEquals(Schema.Kind.DOUBLE, Schemas.schemaOf(double.class).kind());
        assertEquals(Schema.Kind.ENUM, Schemas.schemaOf(Role.class).kind());
        assertEquals(Schema.Kind.UUID, Schemas.schemaOf(UUID.class).kind());
        assertEquals(Schema.Kind.OBJECT, Schemas.schemaOf(Object.class).kind());
    }

    record Containers(Optional<String> optional, List<String> list, Set<Integer> set, Map<String, Integer> map) {
    }

    @Test
    void mapsContainerKinds() {
        List<Schema.Field> fields = Schemas.schemaOf(Containers.class).fields();
        Schema optional = fields.get(0).schema();
        assertEquals(Schema.Kind.OPTIONAL, optional.kind());
        assertEquals(Schema.Kind.STRING, optional.inner().kind());
        Schema list = fields.get(1).schema();
        assertEquals(Schema.Kind.LIST, list.kind());
        assertEquals(Schema.Kind.STRING, list.inner().kind());
        Schema set = fields.get(2).schema();
        assertEquals(Schema.Kind.SET, set.kind());
        assertEquals(Schema.Kind.INT, set.inner().kind());
        Schema map = fields.get(3).schema();
        assertEquals(Schema.Kind.MAP, map.kind());
        assertEquals(Schema.Kind.INT, map.value().kind());
    }

    record AsyncHolder(
            java.util.concurrent.CompletableFuture<String> future,
            java.util.concurrent.CompletionStage<java.util.List<Integer>> stage) {
    }

    @Test
    void unwrapsAsyncWrapperTypes() {
        List<Schema.Field> fields = Schemas.schemaOf(AsyncHolder.class).fields();
        assertEquals(Schema.Kind.STRING, fields.get(0).schema().kind());
        Schema list = fields.get(1).schema();
        assertEquals(Schema.Kind.LIST, list.kind());
        assertEquals(Schema.Kind.INT, list.inner().kind());
    }

    @Test
    void cachesSchemas() {
        assertSame(Schemas.schemaOf(User.class), Schemas.schemaOf(User.class));
    }

    @Test
    void recordFieldsCarryConstraints() {
        Schema schema = Schemas.schemaOf(User.class);
        assertEquals(Schema.Kind.RECORD, schema.kind());
        assertEquals(4, schema.fields().size());

        Schema.Field name = schema.fields().get(0);
        assertEquals("name", name.name());
        assertEquals(2, name.constraints().minLength());
        assertEquals(20, name.constraints().maxLength());
        assertTrue(name.constraints().email());

        Schema.Field age = schema.fields().get(1);
        assertEquals(1, age.constraints().min());

        Schema.Field tags = schema.fields().get(2);
        assertEquals(Schema.Kind.LIST, tags.schema().kind());
        assertEquals(Schema.Kind.STRING, tags.schema().inner().kind());

        Schema.Field nickname = schema.fields().get(3);
        assertEquals(Schema.Kind.OPTIONAL, nickname.schema().kind());
        assertEquals(Schema.Kind.STRING, nickname.schema().inner().kind());
    }
}
