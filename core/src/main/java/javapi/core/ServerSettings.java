package javapi.core;

public record ServerSettings(
        String host,
        int port,
        int workers,
        boolean eventLoopInline,
        boolean logRequests) {

    public ServerSettings {
        host = host == null || host.isBlank() ? "0.0.0.0" : host;
        workers = workers < 0 ? 0 : workers;
    }

    public static ServerSettings create() {
        return new ServerSettings("0.0.0.0", 8080, 0, false, false);
    }

    public ServerSettings withHost(String host) {
        return new ServerSettings(host, port, workers, eventLoopInline, logRequests);
    }

    public ServerSettings withPort(int port) {
        return new ServerSettings(host, port, workers, eventLoopInline, logRequests);
    }

    public ServerSettings withWorkers(int workers) {
        return new ServerSettings(host, port, workers, eventLoopInline, logRequests);
    }

    public ServerSettings withEventLoopInline(boolean eventLoopInline) {
        return new ServerSettings(host, port, workers, eventLoopInline, logRequests);
    }

    public ServerSettings withLogRequests(boolean logRequests) {
        return new ServerSettings(host, port, workers, eventLoopInline, logRequests);
    }
}
