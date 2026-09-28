package io.github.tomdong2010.fkr.dashboard;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.tomdong2010.fkr.config.Settings;
import io.github.tomdong2010.fkr.model.Catalog;
import io.github.tomdong2010.fkr.redis.RedisKeys;
import io.github.tomdong2010.fkr.serde.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.resps.Tuple;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

/**
 * A small HTTP server that reads the job's results from Redis and serves them as JSON, plus a
 * static page that renders them. It uses only the JDK's built-in HTTP server.
 */
public final class DashboardServer implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(DashboardServer.class);
    static final int DEFAULT_WINDOWS = 30;
    private static final int MAX_WINDOWS = RedisKeys.GMV_WINDOWS_KEPT;

    private final JedisPooled redis;
    private final HttpServer server;

    public DashboardServer(String redisHost, int redisPort, int port) throws IOException {
        this.redis = new JedisPooled(redisHost, redisPort);
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.createContext("/api/trending", ex -> respond(ex, () -> Json.MAPPER.writeValueAsBytes(trending()), "application/json"));
        server.createContext("/api/gmv", ex -> respond(ex, () -> Json.MAPPER.writeValueAsBytes(gmv(windows(ex.getRequestURI()))), "application/json"));
        server.createContext("/healthz", ex -> respond(ex, () -> "ok\n".getBytes(StandardCharsets.UTF_8), "text/plain"));
        server.createContext("/", ex -> {
            if (!"/".equals(ex.getRequestURI().getPath())) {
                sendError(ex, 404, "not found");
                return;
            }
            respond(ex, DashboardServer::page, "text/html; charset=utf-8");
        });
    }

    public static void run(Settings settings) throws Exception {
        try (DashboardServer dashboard = new DashboardServer(settings.redisHost, settings.redisPort, settings.dashboardPort)) {
            dashboard.start();
            LOG.info("Dashboard on http://localhost:{} (Redis {}:{})", dashboard.port(), settings.redisHost, settings.redisPort);
            CountDownLatch stop = new CountDownLatch(1);
            Runtime.getRuntime().addShutdownHook(new Thread(stop::countDown));
            stop.await();
        }
    }

    public void start() {
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
        redis.close();
    }

    /** The current leaderboard, best first, with catalog details for display. */
    ObjectNode trending() {
        Map<String, Catalog.Product> catalog = Catalog.byId();
        ObjectNode root = Json.MAPPER.createObjectNode();
        String windowEnd = redis.get(RedisKeys.TRENDING_WINDOW_END);
        root.put("windowEnd", windowEnd == null ? 0 : Long.parseLong(windowEnd));
        ArrayNode items = root.putArray("items");
        List<Tuple> scores = redis.zrevrangeWithScores(RedisKeys.TRENDING, 0, -1);
        int rank = 0;
        for (Tuple t : scores) {
            Catalog.Product p = catalog.get(t.getElement());
            items.addObject()
                    .put("rank", ++rank)
                    .put("productId", t.getElement())
                    .put("name", p == null ? t.getElement() : p.name)
                    .put("category", p == null ? "Other" : p.category)
                    .put("score", (long) t.getScore());
        }
        return root;
    }

    /** Revenue and orders per category for the latest {@code count} windows, oldest first. */
    ObjectNode gmv(int count) {
        ObjectNode root = Json.MAPPER.createObjectNode();
        TreeSet<String> categories = new TreeSet<>();
        Catalog.products().forEach(p -> categories.add(p.category));
        ArrayNode cats = root.putArray("categories");
        categories.forEach(cats::add);

        List<String> starts = redis.zrange(RedisKeys.GMV_WINDOWS, -count, -1);
        ArrayNode windows = root.putArray("windows");
        for (String start : starts) {
            Map<String, String> hash = redis.hgetAll(RedisKeys.gmvWindow(Long.parseLong(start)));
            if (hash.isEmpty()) {
                continue;
            }
            ObjectNode w = windows.addObject().put("start", Long.parseLong(start));
            ObjectNode revenue = w.putObject("revenue");
            ObjectNode orders = w.putObject("orders");
            for (String category : categories) {
                revenue.put(category, Double.parseDouble(hash.getOrDefault(category, "0")));
                orders.put(category, Long.parseLong(hash.getOrDefault(category + ":orders", "0")));
            }
        }
        return root;
    }

    static int windows(URI uri) {
        String query = uri.getQuery();
        if (query != null) {
            for (String part : query.split("&")) {
                if (part.startsWith("windows=")) {
                    try {
                        return Math.max(1, Math.min(MAX_WINDOWS, Integer.parseInt(part.substring(8))));
                    } catch (NumberFormatException e) {
                        return DEFAULT_WINDOWS;
                    }
                }
            }
        }
        return DEFAULT_WINDOWS;
    }

    private static byte[] page() throws IOException {
        try (InputStream in = DashboardServer.class.getResourceAsStream("/dashboard/index.html")) {
            if (in == null) {
                throw new IOException("dashboard/index.html is missing from the classpath");
            }
            return in.readAllBytes();
        }
    }

    @FunctionalInterface
    private interface Body {
        byte[] get() throws Exception;
    }

    private static void respond(HttpExchange ex, Body body, String contentType) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) {
            sendError(ex, 405, "method not allowed");
            return;
        }
        byte[] bytes;
        try {
            bytes = body.get();
        } catch (Exception e) {
            LOG.warn("Request {} failed", ex.getRequestURI(), e);
            sendError(ex, 503, "unavailable: " + e.getClass().getSimpleName());
            return;
        }
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void sendError(HttpExchange ex, int status, String message) throws IOException {
        byte[] bytes = (message + "\n").getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }
}
