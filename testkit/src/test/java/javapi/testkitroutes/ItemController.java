package javapi.testkitroutes;

import javapi.annotations.Body;
import javapi.annotations.File;
import javapi.annotations.Form;
import javapi.annotations.Get;
import javapi.annotations.Path;
import javapi.annotations.Post;
import javapi.annotations.Query;
import javapi.annotations.Route;
import javapi.params.UploadedFile;
import javapi.request.HttpException;
import javapi.request.Response;

@Route("/items")
public class ItemController {

    public record Item(long id, String name, double price) {
    }

    @Get("/:id")
    public Item get(@Path long id) {
        return new Item(id, "item-" + id, 9.99);
    }

    @Get("/echo")
    public String echo(@Query String q) {
        return q;
    }

    @Post
    public Item create(@Body Item body) {
        return new Item(body.id() + 1, body.name(), body.price());
    }

    @Post("/form")
    public String form(@Form String note) {
        return note;
    }

    @Post("/upload")
    public Response upload(@Form String note, @File UploadedFile document) {
        return Response.ok(new UploadResult(note, document.filename(), document.size()));
    }

    @Get("/boom")
    public Item boom() {
        throw new HttpException(418, "teapot");
    }

    public record UploadResult(String note, String filename, long size) {
    }
}
