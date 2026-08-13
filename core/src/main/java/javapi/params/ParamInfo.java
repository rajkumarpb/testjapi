package javapi.params;

import java.lang.reflect.Type;
import javapi.schema.Constraints;

public record ParamInfo(
        String name,
        BindingSource source,
        Type type,
        boolean optional,
        Constraints constraints) {
}
