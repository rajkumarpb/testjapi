package javapi.readme;

import javapi.annotations.Email;
import javapi.annotations.Max;
import javapi.annotations.MaxLength;
import javapi.annotations.Min;
import javapi.annotations.MinLength;
import javapi.annotations.Optional;

public record Item(
        @MinLength(2) @MaxLength(20) String name,
        @Min(1) @Max(1000) int quantity,
        @Email @Optional String supplierEmail) {
}
