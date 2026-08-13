# 9. File uploads & WebSockets

Two real-time / file-handling features in one chapter: multipart uploads with
`@form` + `@file`, and bidirectional WebSocket endpoints.

> **What you'll learn**
>
> - `@form` and `@file` parameter binding, the `UploadedFile` type
> - Multipart and urlencoded form handling
> - Registering WebSocket endpoints with `app.ws(...)`

## File uploads

An endpoint that receives both a text field and a file:

```java
import javapi.annotations.file;
import javapi.annotations.form;
import javapi.annotations.post;
import javapi.request.Response;
import javapi.params.UploadedFile;

@route("/upload")
public class UploadController {

    @post
    public Response upload(@form String note, @file UploadedFile document) {
        return Response.ok(Map.of(
                "note", note,
                "filename", document.filename(),
                "contentType", document.contentType(),
                "size", document.size()));
    }
}
```

`UploadedFile` is a record with `name()` (the form field name), `filename()`,
`contentType()`, `content()` (a defensive copy of the bytes), and `size()`.

Send a multipart request:

```powershell
curl -X POST http://localhost:8080/upload `
  -F "note=quarterly report" `
  -F "document=@report.pdf"
```

```json
{"note":"quarterly report","filename":"report.pdf","contentType":"application/pdf","size":12345}
```

Notes:

- `application/x-www-form-urlencoded` bodies work too — `@form` binds from
  either encoding.
- `@file` parameters are `List<UploadedFile>` if the field repeats.
- Multiple `@file` parameters of different names are fine.
- On the `Request` side: `request.form("note")`, `request.form()`, and
  `request.files()` / `request.file("document")` expose the raw parsed data.

## WebSockets

Register an endpoint with `app.ws(path, endpoint)`:

```java
import javapi.websocket.WebSocketEndpoint;
import javapi.websocket.WebSocketSession;

public class EchoEndpoint implements WebSocketEndpoint {
    @Override
    public void onOpen(WebSocketSession session) {
        session.send("welcome");
    }

    @Override
    public void onMessage(WebSocketSession session, String message) {
        session.send("echo:" + message);
    }

    @Override
    public void onBinary(WebSocketSession session, byte[] bytes) {
        session.send(bytes);
    }

    @Override
    public void onClose(WebSocketSession session, int status, String reason) {
        System.out.println("closed: " + status + " " + reason);
    }
}
```

```java
JavAPI.create()
        .ws("/echo", new EchoEndpoint())
        .scan("com.example")
        .start();
```

`WebSocketSession` offers `send(String)`, `send(byte[])`, `close()`,
`close(status, reason)`, and `isOpen()`.

A quick check with `curl` isn't possible for WebSockets, but the
[testkit](14-testing.md) drives the real pipeline in tests, and any WebSocket
client (browser `WebSocket`, `websocat`, Postman) works against `ws://localhost:8080/echo`.

Next up: [10. Streaming (SSE) & async handlers](10-streaming-sse.md).
