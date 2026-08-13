package demo;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import javapi.annotations.File;
import javapi.annotations.Form;
import javapi.annotations.Get;
import javapi.annotations.Post;
import javapi.annotations.Value;
import javapi.params.UploadedFile;

public class UploadController {

    @Post("/upload")
    public Map<String, Object> upload(
            @Form("note") String note,
            @File("document") UploadedFile document) {
        return Map.of(
                "note", note,
                "filename", document.filename(),
                "contentType", document.contentType(),
                "size", document.size(),
                "text", new String(document.content(), StandardCharsets.UTF_8));
    }

    @Get("/config")
    public Map<String, Object> config(
            @Value("app.title") String title,
            @Value("app.workers") int workers) {
        return Map.of("title", title, "workers", workers);
    }
}
