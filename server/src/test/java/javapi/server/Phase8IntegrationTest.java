package javapi.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import javapi.middleware.Cors;
import javapi.request.Response;
import javapi.routing.RouteScanner;
import javapi.routing.Router;
import javapi.websocket.WebSocketEndpoint;
import javapi.websocket.WebSocketSession;

class Phase8IntegrationTest {

    @TempDir
    Path tempDir;

    private NettyServer server;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        Files.writeString(tempDir.resolve("hello.txt"), "static hello", StandardCharsets.UTF_8);
        Router router = RouteScanner.scan(
                new Router(), "javapi.phase8testroutes", getClass().getClassLoader());
        router.use(Cors.config().origins("https://app.example"));
        router.use(javapi.staticfiles.StaticFiles.fromDirectory("/static", tempDir));
        router.register(javapi.annotations.HttpMethod.GET, "/plain", request -> Response.ok("{\"ok\":true}"));
        router.register(javapi.annotations.HttpMethod.GET, "/plain-map", request -> java.util.Map.of("ok", true));
        router.registerWs("/echo", new WebSocketEndpoint() {
            @Override
            public void onOpen(WebSocketSession session) {
            }

            @Override
            public void onMessage(WebSocketSession session, String message) {
                session.send("echo:" + message);
            }
        });
        server = new NettyServer(router, 0);
        server.start();
        port = server.port();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(5))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void multipartUploadBindsFormAndFile() throws Exception {
        String boundary = "----javapi-test";
        String body = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"note\"\r\n\r\n"
                + "hello upload\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"document\"; filename=\"report.txt\"\r\n"
                + "Content-Type: text/plain\r\n\r\n"
                + "file contents\r\n"
                + "--" + boundary + "--\r\n";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/upload"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(5))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("hello upload"), "got: " + response.body());
        assertTrue(response.body().contains("report.txt"), "got: " + response.body());
        assertTrue(response.body().contains("file contents"), "got: " + response.body());
        assertTrue(response.body().contains("\"size\":13"), "got: " + response.body());
    }

    @Test
    void corsPreflightReturns204() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/echo"))
                .header("Origin", "https://app.example")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "X-Custom")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(5))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(204, response.statusCode());
        assertEquals("https://app.example", response.headers().firstValue("Access-Control-Allow-Origin").orElse(""));
        assertTrue(response.headers().firstValue("Access-Control-Allow-Methods").orElse("").contains("POST"));
    }

    @Test
    void corsActualRequestGetsHeaders() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/static/hello.txt"))
                .header("Origin", "https://app.example")
                .timeout(Duration.ofSeconds(5))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("https://app.example", response.headers().firstValue("Access-Control-Allow-Origin").orElse(""));
    }

    @Test
    void corsAppliedToScannedJsonRoute() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/plain"))
                .header("Origin", "https://app.example")
                .timeout(Duration.ofSeconds(5))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("https://app.example", response.headers().firstValue("Access-Control-Allow-Origin").orElse(""));
    }

    @Test
    void corsAppliedToPlainMapResult() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/plain-map"))
                .header("Origin", "https://app.example")
                .timeout(Duration.ofSeconds(5))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("https://app.example", response.headers().firstValue("Access-Control-Allow-Origin").orElse(""));
    }

    @Test
    void staticFileServedFromDisk() throws Exception {
        HttpResponse<String> response = get("/static/hello.txt");
        assertEquals(200, response.statusCode());
        assertEquals("static hello", response.body());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("text/plain"));
    }

    @Test
    void staticPathTraversalRejected() throws Exception {
        HttpResponse<String> response = get("/static/../secret.txt");
        assertEquals(404, response.statusCode());
    }

    @Test
    void sseStreamsEventsThenCloses() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/stream"))
                .timeout(Duration.ofSeconds(10))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("text/event-stream"));
        assertTrue(response.body().contains("data: tick-1"), "got: " + response.body());
        assertTrue(response.body().contains("data: tick-3"), "got: " + response.body());
    }

    @Test
    void webSocketUpgradeAndEcho() throws Exception {
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        CompletableFuture<String> received = new CompletableFuture<>();
        try {
            WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                    URI.create("ws://localhost:" + port + "/echo"),
                    io.netty.handler.codec.http.websocketx.WebSocketVersion.V13,
                    null, true, new DefaultHttpHeaders());
            Bootstrap bootstrap = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new HttpClientCodec(),
                                    new HttpObjectAggregator(65536),
                                    new ChannelInboundHandlerAdapter() {
                                        @Override
                                        public void channelActive(ChannelHandlerContext ctx) {
                                            handshaker.handshake(ctx.channel());
                                        }

                                        @Override
                                        public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                            if (msg instanceof io.netty.handler.codec.http.FullHttpResponse response) {
                                                if (!handshaker.isHandshakeComplete()) {
                                                    handshaker.finishHandshake(ctx.channel(), response);
                                                }
                                                return;
                                            }
                                            if (msg instanceof TextWebSocketFrame text) {
                                                received.complete(text.text());
                                                ctx.close();
                                            }
                                        }
                                    });
                        }
                    });
            Channel channel = bootstrap.connect("localhost", port).sync().channel();
            awaitTrue(() -> handshaker.isHandshakeComplete(), "websocket handshake did not complete");
            channel.writeAndFlush(new TextWebSocketFrame("ping"));
            String reply = received.get(5, TimeUnit.SECONDS);
            assertEquals("echo:ping", reply);
        } finally {
            group.shutdownGracefully();
        }
    }

    private static void awaitTrue(BooleanSupplier condition, String message) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
        fail(message);
    }
}
