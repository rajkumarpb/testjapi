package demo;

import javapi.annotations.email;
import javapi.annotations.max;
import javapi.annotations.maxlength;
import javapi.annotations.min;
import javapi.annotations.minlength;
import javapi.annotations.optional;

public record Item(
        @minlength(2) @maxlength(20) String name,
        @min(1) @max(1000) int quantity,
        @email @optional String supplierEmail) {
}
