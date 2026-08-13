package javapi.request;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javapi.params.UploadedFile;

public final class Request {

    private final String method;
    private final String uri;
    private final String path;
    private final String query;
    private final Map<String, String> pathParams;
    private final Iterable<Map.Entry<String, String>> headerSource;
    private final String cookieHeader;
    private final Map<String, String> form;
    private final List<UploadedFile> files;
    private final String body;

    private volatile Map<String, String> loweredHeaders;
    private volatile Map<String, String> parsedQueryParams;
    private volatile Map<String, String> parsedCookies;

    private Request(Builder builder) {
        this.method = builder.method;
        this.uri = builder.uri;
        String rawPath = builder.uri;
        String rawQuery = "";
        int queryIndex = builder.uri.indexOf('?');
        if (queryIndex >= 0) {
            rawQuery = builder.uri.substring(queryIndex + 1);
            rawPath = builder.uri.substring(0, queryIndex);
        }
        this.path = builder.path.isEmpty() ? rawPath : builder.path;
        this.query = builder.query == null ? rawQuery : builder.query;
        this.pathParams = Map.copyOf(builder.pathParams);
        this.headerSource = builder.headerSource;
        this.cookieHeader = builder.cookieHeader == null ? "" : builder.cookieHeader;
        this.form = Map.copyOf(builder.form);
        this.files = List.copyOf(builder.files);
        this.body = builder.body == null ? "" : builder.body;
    }

    private Map<String, String> lowerHeaders() {
        Map<String, String> lower = new HashMap<>();
        for (Map.Entry<String, String> entry : headerSource) {
            lower.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
        }
        return Map.copyOf(lower);
    }

    private Request(String method, String uri, String path, String query, Map<String, String> pathParams,
            Iterable<Map.Entry<String, String>> headerSource, String cookieHeader, Map<String, String> form,
            List<UploadedFile> files, String body) {
        this.method = method;
        this.uri = uri;
        this.path = path;
        this.query = query;
        this.pathParams = Map.copyOf(pathParams);
        this.headerSource = headerSource;
        this.cookieHeader = cookieHeader;
        this.form = form;
        this.files = files;
        this.body = body;
    }

    /**
     * Copy this request with different path params, reusing the already-parsed
     * headers/query/cookies instead of re-parsing them. The dispatch path calls
     * this per request after routing, so it must stay allocation-cheap.
     */
    public Request withPathParams(Map<String, String> pathParams) {
        return new Request(method, uri, path, query, pathParams, headerSource, cookieHeader, form, files, body);
    }

    public String method() {
        return method;
    }

    public String uri() {
        return uri;
    }

    public String path() {
        return path;
    }

    public String query() {
        return query;
    }

    public Map<String, String> pathParams() {
        return pathParams;
    }

    public String pathParam(String name) {
        return pathParams.get(name);
    }

    public String queryParam(String name) {
        return queryParams().get(name);
    }

    public Map<String, String> queryParams() {
        Map<String, String> result = parsedQueryParams;
        if (result == null) {
            result = QueryString.parse(query);
            parsedQueryParams = result;
        }
        return result;
    }

    public String header(String name) {
        return headers().get(name.toLowerCase(Locale.ROOT));
    }

    public Map<String, String> headers() {
        Map<String, String> result = loweredHeaders;
        if (result == null) {
            result = lowerHeaders();
            loweredHeaders = result;
        }
        return result;
    }

    public String cookieHeader() {
        return cookieHeader;
    }

    public String cookie(String name) {
        return cookies().get(name);
    }

    public Map<String, String> cookies() {
        Map<String, String> result = parsedCookies;
        if (result == null) {
            result = Cookies.parse(cookieHeader);
            parsedCookies = result;
        }
        return result;
    }

    public String body() {
        return body;
    }

    public Map<String, String> form() {
        return form;
    }

    public String form(String name) {
        return form.get(name);
    }

    public List<UploadedFile> files() {
        return files;
    }

    public UploadedFile file(String name) {
        for (UploadedFile file : files) {
            if (file.name().equals(name)) {
                return file;
            }
        }
        return null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String method = "";
        private String uri = "";
        private String path = "";
        private Map<String, String> pathParams = Map.of();
        private String query;
        private Iterable<Map.Entry<String, String>> headerSource = Map.<String, String>of().entrySet();
        private String cookieHeader = "";
        private Map<String, String> form = Map.of();
        private List<UploadedFile> files = List.of();
        private String body = "";

        public Builder method(String method) {
            this.method = method;
            return this;
        }

        public Builder uri(String uri) {
            this.uri = uri;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder pathParams(Map<String, String> pathParams) {
            this.pathParams = pathParams;
            return this;
        }

        public Builder query(String query) {
            this.query = query;
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headerSource = headers.entrySet();
            return this;
        }

        /**
         * Raw header source materialized lazily on first access. The iterable
         * must remain valid for the request's lifetime (for the inline
         * event-loop path it is read before the underlying message is released).
         */
        public Builder headerEntries(Iterable<Map.Entry<String, String>> headerEntries) {
            this.headerSource = headerEntries;
            return this;
        }

        public Builder cookieHeader(String cookieHeader) {
            this.cookieHeader = cookieHeader;
            return this;
        }

        public Builder body(String body) {
            this.body = body;
            return this;
        }

        public Builder form(Map<String, String> form) {
            this.form = form;
            return this;
        }

        public Builder files(List<UploadedFile> files) {
            this.files = files;
            return this;
        }

        public Request build() {
            return new Request(this);
        }
    }
}
