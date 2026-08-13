package javapi.openapi.testroutes;

public record Thing(
        @javapi.annotations.MinLength(2) String name,
        Integer qty,
        java.util.Optional<String> note) {
}
