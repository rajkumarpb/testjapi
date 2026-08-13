package javapi.jdbcroutes;

import java.util.Map;
import javapi.annotations.Depends;
import javapi.annotations.Get;
import javapi.annotations.Path;
import javapi.annotations.Post;
import javapi.annotations.Query;
import javapi.annotations.Transaction;
import javapi.jdbc.Jdbc;
import javapi.jdbc.RowMapper;

public class ItemsController {

    @Get("/items/:id")
    public java.util.Optional<Item> get(@Depends Jdbc db, @Path long id) {
        return db.findOne("SELECT * FROM items WHERE id = ?", RowMapper.from(Item.class), id);
    }

    @Post("/items")
    @Transaction
    public Map<String, Object> create(@Depends Jdbc db, @Query String name, @Query int qty) {
        long id = db.insert("INSERT INTO items(name, qty, status) VALUES (?,?,?)",
                name, qty, Item.Status.NEW.name());
        return Map.of("id", id);
    }

    @Post("/items/fail")
    @Transaction
    public Map<String, Object> fail(@Depends Jdbc db) {
        db.update("INSERT INTO items(name, qty) VALUES (?,?)", "ghost", 1);
        throw new IllegalStateException("boom");
    }
}
