package javapi.jdbcroutes;

import java.util.Map;
import javapi.annotations.depends;
import javapi.annotations.get;
import javapi.annotations.path;
import javapi.annotations.post;
import javapi.annotations.query;
import javapi.annotations.transaction;
import javapi.jdbc.Jdbc;
import javapi.jdbc.RowMapper;

public class ItemsController {

    @get("/items/:id")
    public java.util.Optional<Item> get(@depends Jdbc db, @path long id) {
        return db.findOne("SELECT * FROM items WHERE id = ?", RowMapper.from(Item.class), id);
    }

    @post("/items")
    @transaction
    public Map<String, Object> create(@depends Jdbc db, @query String name, @query int qty) {
        long id = db.insert("INSERT INTO items(name, qty, status) VALUES (?,?,?)",
                name, qty, Item.Status.NEW.name());
        return Map.of("id", id);
    }

    @post("/items/fail")
    @transaction
    public Map<String, Object> fail(@depends Jdbc db) {
        db.update("INSERT INTO items(name, qty) VALUES (?,?)", "ghost", 1);
        throw new IllegalStateException("boom");
    }
}
