package javapi.jdbcroutes;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public record Item(
        long id,
        String name,
        int qty,
        Optional<String> note,
        Status status,
        LocalDate created,
        UUID uid) {

    public enum Status {
        NEW, DONE
    }
}
