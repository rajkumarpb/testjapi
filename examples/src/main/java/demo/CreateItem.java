package demo;

import javapi.annotations.Optional;

public record CreateItem(String name, int quantity, @Optional String supplierEmail) {
}
