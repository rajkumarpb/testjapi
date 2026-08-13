package javapi.phase8routes;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import javapi.annotations.file;
import javapi.annotations.form;
import javapi.annotations.get;
import javapi.annotations.post;
import javapi.annotations.value;
import javapi.params.UploadedFile;

public class UploadController {

    @post("/upload")
    public Map<String, Object> upload(
            @form("note") String note,
            @file("document") UploadedFile document) {
        return Map.of(
                "note", note,
                "filename", document.filename(),
                "contentType", document.contentType(),
                "size", document.size(),
                "text", new String(document.content(), StandardCharsets.UTF_8));
    }

    @get("/config")
    public Map<String, Object> config(@value("feature.flag") boolean flag) {
        return Map.of("flag", flag);
    }
}
