package demo;

import java.util.List;
import java.util.Map;
import javapi.annotations.Body;
import javapi.annotations.Depends;
import javapi.annotations.Get;
import javapi.annotations.Path;
import javapi.annotations.Post;
import javapi.annotations.Query;
import javapi.annotations.Route;
import javapi.annotations.Transaction;
import javapi.jdbc.Jdbc;
import javapi.jdbc.RowMapper;
import javapi.request.HttpException;

@Route("/db")
public class JdbcController {

    @Get("/items")
    public List<DbItem> list(@Depends Jdbc db) {
        return db.query("SELECT * FROM db_items ORDER BY id", RowMapper.from(DbItem.class));
    }

    @Get("/items/:id")
    public DbItem item(@Depends Jdbc db, @Path long id) {
        return db.findOne("SELECT * FROM db_items WHERE id = ?", RowMapper.from(DbItem.class), id)
                .orElseThrow(() -> new HttpException(404, "Item not found"));
    }

    @Post("/items")
    public Map<String, Object> create(@Depends Jdbc db, @Body CreateItem input) {
        long id = db.insert("INSERT INTO db_items(name, quantity, supplier_email) VALUES (?,?,?)",
                input.name(), input.quantity(), input.supplierEmail());
        return Map.of("id", id);
    }

    @Post("/transfer")
    @Transaction
    public Map<String, Object> transfer(@Depends Jdbc db,
            @Query("from") long from, @Query("to") long to, @Query("amount") int amount) {
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
