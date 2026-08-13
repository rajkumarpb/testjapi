package demo;

import java.util.List;
import java.util.Map;
import javapi.annotations.body;
import javapi.annotations.depends;
import javapi.annotations.get;
import javapi.annotations.path;
import javapi.annotations.post;
import javapi.annotations.query;
import javapi.annotations.route;
import javapi.annotations.transaction;
import javapi.jdbc.Jdbc;
import javapi.jdbc.RowMapper;
import javapi.request.HttpException;

@route("/db")
public class JdbcController {

    @get("/items")
    public List<DbItem> list(@depends Jdbc db) {
        return db.query("SELECT * FROM db_items ORDER BY id", RowMapper.from(DbItem.class));
    }

    @get("/items/:id")
    public DbItem item(@depends Jdbc db, @path long id) {
        return db.findOne("SELECT * FROM db_items WHERE id = ?", RowMapper.from(DbItem.class), id)
                .orElseThrow(() -> new HttpException(404, "Item not found"));
    }

    @post("/items")
    public Map<String, Object> create(@depends Jdbc db, @body CreateItem input) {
        long id = db.insert("INSERT INTO db_items(name, quantity, supplier_email) VALUES (?,?,?)",
                input.name(), input.quantity(), input.supplierEmail());
        return Map.of("id", id);
    }

    @post("/transfer")
    @transaction
    public Map<String, Object> transfer(@depends Jdbc db,
            @query("from") long from, @query("to") long to, @query("amount") int amount) {
        int moved = db.update("UPDATE db_items SET quantity = quantity - ? WHERE id = ?", amount, from);
        if (moved != 1) {
            throw new HttpException(404, "Source item not found");
        }
        int credited = db.update("UPDATE db_items SET quantity = quantity + ? WHERE id = ?", amount, to);
        if (credited != 1) {
            throw new HttpException(404, "Destination item not found");
        }
        return Map.of("ok", true);
    }
}
