package javapi.server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.util.ReferenceCountUtil;
import javapi.annotations.HttpMethod;
import javapi.json.Json;
import javapi.middleware.Middleware;
import javapi.middleware.MiddlewareChain;
import javapi.params.UploadedFile;
import javapi.request.Request;
import javapi.request.Response;
import javapi.routing.ExecutionMode;
import javapi.routing.RouteMatch;
import javapi.routing.Router;
import javapi.sse.SseEmitter;
import javapi.websocket.WebSocketEndpoint;
import javapi.websocket.WebSocketSession;

final class HttpHandler extends SimpleChannelInboundHandler<Object> {

    private final Router router;
    private final ExecutorService executor;
    private final boolean eventLoopInlineDefault;
    private final boolean logRequests;

    private WebSocketServerHandshaker webSocketHandshaker;
    private WebSocketEndpoint activeEndpoint;
    private WebSocketSession activeSession;

    HttpHandler(Router router, ExecutorService executor, boolean eventLoopInlineDefault, boolean logRequests) {
        this.router = router;
        this.executor = executor;
        this.eventLoopInlineDefault = eventLoopInlineDefault;
        this.logRequests = logRequests;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object message) {
        if (message instanceof WebSocketFrame frame) {
            handleFrame(ctx, frame);
            return;
        }
        if (message instanceof FullHttpRequest request) {
            handleHttp(ctx, request);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (activeEndpoint != null && activeSession != null) {
            activeEndpoint.onClose(activeSession, 1006, "connection closed");
        }
        ctx.fireChannelInactive();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (activeEndpoint != null && activeSession != null) {
            activeEndpoint.onError(activeSession, cause);
        }
        if (logRequests) {
            System.err.println("[javapi] connection error: " + cause);
        }
        ctx.close();
    }

    private void handleHttp(ChannelHandlerContext ctx, FullHttpRequest nettyRequest) {
        if (isWebSocketUpgrade(nettyRequest)) {
            handleWebSocketUpgrade(ctx, nettyRequest);
            return;
        }
        RouteMatch inlineMatch = isInlineMatch(nettyRequest);
        boolean inline = inlineMatch != null;
        MultipartData multipart = parseMultipart(nettyRequest);
        Map<String, String> form = Map.of();
        Map<String, String> urlencoded = parseFormFields(nettyRequest);
        if (!urlencoded.isEmpty() || !multipart.form().isEmpty()) {
            Map<String, String> merged = new HashMap<>(urlencoded.size() + multipart.form().size());
            merged.putAll(urlencoded);
            merged.putAll(multipart.form());
            form = Map.copyOf(merged);
        }
        String body = nettyRequest.content().readableBytes() == 0 ? ""
                : nettyRequest.content().toString(StandardCharsets.UTF_8);
        Request.Builder builder = Request.builder()
                .method(nettyRequest.method().name())
                .uri(nettyRequest.uri())
                .pathParams(inlineMatch == null ? Map.of() : inlineMatch.pathParams())
                .cookieHeader(nettyRequest.headers().get(HttpHeaderNames.COOKIE))
                .body(body)
                .form(form)
                .files(multipart.files());
        Request javapiRequest;
        if (inline) {
            javapiRequest = builder.headerEntries(nettyRequest.headers()).build();
        } else {
            javapiRequest = builder.headers(headers(nettyRequest)).build();
        }
        boolean keepAlive = HttpUtil.isKeepAlive(nettyRequest);
        boolean head = nettyRequest.method().name().equalsIgnoreCase("HEAD");
        if (inline) {
            process(ctx, javapiRequest, keepAlive, head, inlineMatch);
        } else {
            executor.execute(() -> process(ctx, javapiRequest, keepAlive, head, inlineMatch));
        }
    }

    private void process(ChannelHandlerContext ctx, Request request, boolean keepAlive, boolean head,
            RouteMatch inlineMatch) {
        long start = logRequests ? System.nanoTime() : 0;
        Object result;
        try {
            List<Middleware> middleware = router.middleware();
            if (middleware.isEmpty()) {
                result = dispatch(request, inlineMatch);
            } else {
                result = MiddlewareChain.build(middleware, this::dispatch).next(request);
            }
        } catch (Throwable t) {
            result = router.mapError(t);
        }
        if (isAsync(result)) {
            if (inlineMatch != null) {
                Object async = result;
                executor.execute(() -> finish(ctx, resolveAsync(async), keepAlive, head, request, start));
                return;
            }
            result = resolveAsync(result);
        }
        finish(ctx, result, keepAlive, head, request, start);
    }

    private Object dispatch(Request request) {
        return dispatch(request, null);
    }

    private Object dispatch(Request request, RouteMatch prematched) {
        String methodName = request.method();
        HttpMethod method = parseMethod(methodName);
        if (method == null) {
            return Response.of(400, Map.of("detail", "Unsupported method"));
        }
        RouteMatch match = prematched != null ? prematched : router.match(method, request.path());
        if (match == null) {
            Set<HttpMethod> allowed = router.allowedMethods(request.path());
            if (allowed.isEmpty()) {
                return Response.of(404, Map.of("detail", "Not Found"));
            }
            return Response.of(405, Map.of("detail", "Method Not Allowed")).withHeader("Allow", join(allowed));
        }
        Request bound = prematched != null ? request : request.withPathParams(match.pathParams());
        try {
            Object handled = match.route().handler().handle(bound);
            if (handled instanceof Response || handled instanceof SseEmitter || isAsync(handled)) {
                return handled;
            }
            return Response.ok(handled);
        } catch (Throwable t) {
            return router.mapError(t);
        }
    }

    private Object resolveAsync(Object result) {
        try {
            return await(result);
        } catch (Throwable t) {
            return router.mapError(t);
        }
    }

    private void finish(ChannelHandlerContext ctx, Object result, boolean keepAlive, boolean head,
            Request request, long startNanos) {
        if (result instanceof SseEmitter emitter) {
            writeSse(ctx, emitter, request, startNanos);
            return;
        }
        Response response = result instanceof Response r ? r : Response.ok(result);
        write(ctx, response, keepAlive, head, null, request.method(), request.path(), startNanos);
        response.backgroundTasks().run();
    }

    private void writeSse(ChannelHandlerContext ctx, SseEmitter emitter, Request request, long startNanos) {
        DefaultHttpResponse head = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        head.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/event-stream; charset=utf-8");
        head.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-cache");
        head.headers().set("X-Accel-Buffering", "no");
        head.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        head.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        ctx.writeAndFlush(head);
        emitter.attach(text -> ctx.writeAndFlush(new DefaultHttpContent(
                Unpooled.copiedBuffer(text, StandardCharsets.UTF_8))), () -> {
            ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
                    .addListener(ChannelFutureListener.CLOSE);
        });
        if (logRequests) {
            long millis = (System.nanoTime() - startNanos) / 1_000_000;
            System.out.println("[javapi] SSE " + request.method() + " " + request.path() + " 200 " + millis + "ms");
        }
    }

    private static boolean isAsync(Object result) {
        return result instanceof CompletableFuture<?>
                || result instanceof CompletionStage<?>
                || result instanceof Future<?>;
    }

    private static Object await(Object result) {
        if (result instanceof CompletableFuture<?> future) {
            try {
                return future.join();
            } catch (CompletionException e) {
                return sneakyThrow(e.getCause() == null ? e : e.getCause());
            }
        }
        if (result instanceof CompletionStage<?> stage) {
            try {
                return stage.toCompletableFuture().join();
            } catch (CompletionException e) {
                return sneakyThrow(e.getCause() == null ? e : e.getCause());
            }
        }
        try {
            return ((Future<?>) result).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while awaiting handler result", e);
        } catch (ExecutionException e) {
            return sneakyThrow(e.getCause() == null ? e : e.getCause());
        }
    }

    private void write(ChannelHandlerContext ctx, Response response,
            boolean keepAlive, boolean head, String allow, String method, String path, long startNanos) {
        ByteBuf buffer;
        int length;
        if (response.body() instanceof byte[] bytes) {
            String contentType = contentType(response, "application/octet-stream");
            if (head) {
                length = bytes.length;
                buffer = Unpooled.EMPTY_BUFFER;
            } else {
                buffer = ctx.alloc().buffer(bytes.length).writeBytes(bytes);
                length = bytes.length;
            }
            writeResponse(ctx, response, keepAlive, head, allow, method, path, startNanos, buffer, length,
                    contentType);
            return;
        }
        String contentType = contentType(response, "application/json; charset=UTF-8");
        boolean json = contentType.regionMatches(true, 0, "application/json", 0, 16);
        ByteBuf text;
        if (json && response.body() != null) {
            if (head) {
                String content = Json.write(response.body());
                length = ByteBufUtil.utf8Bytes(content);
                text = Unpooled.EMPTY_BUFFER;
            } else {
                text = ctx.alloc().buffer();
                Json.writeTo(response.body(), new ByteBufJsonOutput(text));
                length = text.readableBytes();
            }
        } else {
            String content = response.body() == null ? "" : String.valueOf(response.body());
            if (head) {
                length = ByteBufUtil.utf8Bytes(content);
                text = Unpooled.EMPTY_BUFFER;
            } else {
                text = ctx.alloc().buffer();
                ByteBufUtil.writeUtf8(text, content);
                length = text.readableBytes();
            }
        }
        writeResponse(ctx, response, keepAlive, head, allow, method, path, startNanos, text, length, contentType);
    }

    private void writeResponse(ChannelHandlerContext ctx, Response response,
            boolean keepAlive, boolean head, String allow, String method, String path, long startNanos,
            ByteBuf buffer, int length, String contentType) {
        FullHttpResponse netty = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.valueOf(response.status()),
                buffer);
        netty.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
        netty.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, length);
        for (Map.Entry<String, String> header : response.headers().entrySet()) {
            netty.headers().set(header.getKey(), header.getValue());
        }
        if (allow != null) {
            netty.headers().set(HttpHeaderNames.ALLOW, allow);
        }
        if (keepAlive) {
            netty.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        } else {
            netty.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        }
        ctx.writeAndFlush(netty).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
        if (!keepAlive) {
            ctx.close();
        }
        if (logRequests) {
            long millis = (System.nanoTime() - startNanos) / 1_000_000;
            System.out.println("[javapi] " + method + " " + path + " " + response.status() + " " + millis + "ms");
        }
    }

    private void handleWebSocketUpgrade(ChannelHandlerContext ctx, FullHttpRequest request) {
        String path = pathOf(request.uri());
        WebSocketEndpoint endpoint = router.wsHandler(path);
        if (endpoint == null) {
            ctx.writeAndFlush(new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.NOT_FOUND,
                    Unpooled.EMPTY_BUFFER));
            ctx.close();
            return;
        }
        String host = request.headers().get(HttpHeaderNames.HOST);
        String location = "ws://" + (host == null ? "localhost" : host) + path;
        WebSocketServerHandshakerFactory factory = new WebSocketServerHandshakerFactory(location, null, true);
        WebSocketServerHandshaker handshaker = factory.newHandshaker(request);
        if (handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
            return;
        }
        webSocketHandshaker = handshaker;
        activeEndpoint = endpoint;
        handshaker.handshake(ctx.channel(), request).addListener(future -> {
            if (future.isSuccess()) {
                activeSession = new NettyWebSocketSession(ctx.channel(), handshaker);
                endpoint.onOpen(activeSession);
            } else {
                activeEndpoint = null;
                ctx.close();
            }
        });
    }

    private void handleFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
        if (frame instanceof CloseWebSocketFrame close) {
            WebSocketEndpoint endpoint = activeEndpoint;
            WebSocketSession session = activeSession;
            if (webSocketHandshaker != null) {
                webSocketHandshaker.close(ctx.channel(), close.retain());
            }
            if (endpoint != null && session != null) {
                endpoint.onClose(session, close.statusCode(), close.reasonText());
            }
            activeEndpoint = null;
            return;
        }
        WebSocketEndpoint endpoint = activeEndpoint;
        WebSocketSession session = activeSession;
        if (endpoint == null || session == null) {
            ctx.close();
            return;
        }
        if (frame instanceof io.netty.handler.codec.http.websocketx.TextWebSocketFrame text) {
            try {
                endpoint.onMessage(session, text.text());
            } catch (Throwable t) {
                endpoint.onError(session, t);
            }
        } else if (frame instanceof io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame binary) {
            ByteBuf content = binary.content();
            byte[] data = new byte[content.readableBytes()];
            content.readBytes(data);
            try {
                endpoint.onBinary(session, data);
            } catch (Throwable t) {
                endpoint.onError(session, t);
            }
        }
    }

    private Map<String, String> parseFormFields(FullHttpRequest request) {
        String contentType = request.headers().get(HttpHeaderNames.CONTENT_TYPE);
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT)
                .startsWith("application/x-www-form-urlencoded")) {
            return Map.of();
        }
        return javapi.request.QueryString.parse(request.content().toString(StandardCharsets.UTF_8));
    }

    private static final MultipartData EMPTY_MULTIPART = new MultipartData(Map.of(), List.of());

    private MultipartData parseMultipart(FullHttpRequest request) {
        String contentType = request.headers().get(HttpHeaderNames.CONTENT_TYPE);
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT)
                .startsWith("multipart/form-data")) {
            return EMPTY_MULTIPART;
        }
        Map<String, String> form = new HashMap<>();
        List<UploadedFile> files = new ArrayList<>();
        HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(
                new DefaultHttpDataFactory(false), request, StandardCharsets.UTF_8);
        try {
            for (InterfaceHttpData data : decoder.getBodyHttpDatas()) {
                try {
                    if (data instanceof FileUpload upload) {
                        ByteBuf content = upload.content();
                        byte[] bytes = ByteBufUtil.getBytes(content);
                        files.add(new UploadedFile(
                                upload.getName(), upload.getFilename(), upload.getContentType(), bytes));
                    } else if (data instanceof Attribute attribute) {
                        try {
                            form.put(attribute.getName(), attribute.getValue());
                        } catch (IOException e) {
                            throw sneakyThrow(e);
                        }
                    }
                } finally {
                    ReferenceCountUtil.release(data);
                }
            }
        } finally {
            decoder.destroy();
        }
        return new MultipartData(Map.copyOf(form), List.copyOf(files));
    }

    private record MultipartData(Map<String, String> form, List<UploadedFile> files) {
    }

    private RouteMatch isInlineMatch(FullHttpRequest request) {
        HttpMethod method = parseMethod(request.method().name());
        String path = pathOf(request.uri());
        RouteMatch match = method == null ? null : router.match(method, path);
        if (match == null) {
            return null;
        }
        ExecutionMode execution = match.route().execution();
        return (execution == ExecutionMode.EVENT_LOOP
                || (execution == ExecutionMode.AUTO && eventLoopInlineDefault)) ? match : null;
    }

    private static boolean isWebSocketUpgrade(FullHttpRequest request) {
        String upgrade = request.headers().get(HttpHeaderNames.UPGRADE);
        String connection = request.headers().get(HttpHeaderNames.CONNECTION);
        return upgrade != null && "websocket".equalsIgnoreCase(upgrade.trim())
                && connection != null && connection.toLowerCase(Locale.ROOT).contains("upgrade");
    }

    private static String pathOf(String uri) {
        int queryIndex = uri.indexOf('?');
        return queryIndex >= 0 ? uri.substring(0, queryIndex) : uri;
    }

    private static HttpMethod parseMethod(String name) {
        try {
            return HttpMethod.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String contentType(Response response, String defaultContentType) {
        for (Map.Entry<String, String> header : response.headers().entrySet()) {
            if (header.getKey().equalsIgnoreCase(HttpHeaderNames.CONTENT_TYPE.toString())) {
                return header.getValue();
            }
        }
        return defaultContentType;
    }

    private static Map<String, String> headers(FullHttpRequest request) {
        Map<String, String> headers = new HashMap<>();
        for (Map.Entry<String, String> entry : request.headers()) {
            headers.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
        }
        return headers;
    }

    private static String join(Set<HttpMethod> methods) {
        StringJoiner joiner = new StringJoiner(", ");
        for (HttpMethod method : methods) {
            joiner.add(method.name());
        }
        return joiner.toString();
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException sneakyThrow(Throwable throwable) throws T {
        throw (T) throwable;
    }
}
