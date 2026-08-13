package demo;

import javapi.annotations.optional;

public record CreateItem(String name, int quantity, @optional String supplierEmail) {
}
