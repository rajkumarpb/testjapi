package javapi.testkitroutes;

import javapi.annotations.body;
import javapi.annotations.file;
import javapi.annotations.form;
import javapi.annotations.get;
import javapi.annotations.path;
import javapi.annotations.post;
import javapi.annotations.query;
import javapi.annotations.route;
import javapi.params.UploadedFile;
import javapi.request.HttpException;
import javapi.request.Response;

@route("/items")
public class ItemController {

    public record Item(long id, String name, double price) {
    }

    @get("/:id")
    public Item get(@path long id) {
        return new Item(id, "item-" + id, 9.99);
    }

    @get("/echo")
    public String echo(@query String q) {
        return q;
    }

    @post
    public Item create(@body Item body) {
        return new Item(body.id() + 1, body.name(), body.price());
    }

    @post("/form")
    public String form(@form String note) {
        return note;
    }

    @post("/upload")
    public Response upload(@form String note, @file UploadedFile document) {
        return Response.ok(new UploadResult(note, document.filename(), document.size()));
    }

    @get("/boom")
    public Item boom() {
        throw new HttpException(418, "teapot");
    }

    public record UploadResult(String note, String filename, long size) {
    }
}
