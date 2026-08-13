package javapi.server;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import javapi.core.Server;
import javapi.core.ServerSettings;
import javapi.routing.Router;

public final class NettyServer implements Server {

    private static final int MAX_BODY_BYTES = 16 * 1024 * 1024;

    private final Router router;
    private final ServerSettings settings;
    private final CountDownLatch stopped = new CountDownLatch(1);
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile int boundPort;
    private NioEventLoopGroup bossGroup;
    private NioEventLoopGroup workerGroup;
    private ExecutorService executor;
    private Channel serverChannel;

    public NettyServer(Router router, int port) {
        this(router, ServerSettings.create().withPort(port));
    }

    public NettyServer(Router router, ServerSettings settings) {
        this.router = router;
        this.settings = settings;
    }

    @Override
    public Server start() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::close));
        bossGroup = new NioEventLoopGroup(1);
        int workers = settings.workers();
        workerGroup = workers > 0 ? new NioEventLoopGroup(workers) : new NioEventLoopGroup();
        executor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("javapi-vt-", 0).factory());
        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) {
                        channel.pipeline().addLast(new HttpServerCodec());
                        channel.pipeline().addLast(new HttpObjectAggregator(MAX_BODY_BYTES));
                        channel.pipeline().addLast(new HttpHandler(
                                router, executor, settings.eventLoopInline(), settings.logRequests()));
                    }
                });
        serverChannel = bootstrap.bind(settings.host(), settings.port()).syncUninterruptibly().channel();
        boundPort = ((InetSocketAddress) serverChannel.localAddress()).getPort();
        return this;
    }

    @Override
    public int port() {
        return boundPort;
    }

    @Override
    public void await() throws InterruptedException {
        stopped.await();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
        }
        if (executor != null) {
            executor.shutdown();
            try {
                executor.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully().syncUninterruptibly();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully().syncUninterruptibly();
        }
        stopped.countDown();
    }
}
