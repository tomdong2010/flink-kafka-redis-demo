package io.github.tomdong2010.fkr.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.tomdong2010.fkr.redis.RedisKeys;
import io.github.tomdong2010.fkr.redis.RedisTestSupport;
import io.github.tomdong2010.fkr.serde.Json;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;
import redis.clients.jedis.Jedis;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static io.github.tomdong2010.fkr.redis.RedisTestSupport.REDIS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class DashboardServerTest {
    private static DashboardServer server;
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @BeforeAll
    static void start() throws Exception {
        RedisTestSupport.start();
        try (Jedis jedis = new Jedis(REDIS.getHost(), REDIS.getFirstMappedPort())) {
            jedis.flushAll();
            jedis.zadd(RedisKeys.TRENDING, 9, "p02");
            jedis.zadd(RedisKeys.TRENDING, 4, "p06");
            jedis.set(RedisKeys.TRENDING_WINDOW_END, "1700000010000");
            jedis.hset(RedisKeys.gmvWindow(1_700_000_000_000L), "Books", "42.0");
            jedis.hset(RedisKeys.gmvWindow(1_700_000_000_000L), "Books:orders", "1");
            jedis.zadd(RedisKeys.GMV_WINDOWS, 1_700_000_000_000L, "1700000000000");
        }
        server = new DashboardServer(REDIS.getHost(), REDIS.getFirstMappedPort(), 0);
        server.start();
    }

    @AfterAll
    static void stop() {
        if (server != null) {
            server.close();
        }
    }

    private static HttpResponse<String> get(String path) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void trendingIsRankedAndEnriched() throws Exception {
        HttpResponse<String> res = get("/api/trending");
        assertEquals(200, res.statusCode());
        JsonNode body = Json.MAPPER.readTree(res.body());
        assertEquals(1_700_000_010_000L, body.get("windowEnd").asLong());
        assertEquals(2, body.get("items").size());
        JsonNode first = body.get("items").get(0);
        assertEquals(1, first.get("rank").asInt());
        assertEquals("p02", first.get("productId").asText());
        assertEquals("Noise-Cancelling Headphones", first.get("name").asText());
        assertEquals("Electronics", first.get("category").asText());
        assertEquals(9, first.get("score").asLong());
    }

    @Test
    void gmvListsEveryCategoryPerWindow() throws Exception {
        JsonNode body = Json.MAPPER.readTree(get("/api/gmv?windows=5").body());
        assertEquals(6, body.get("categories").size());
        JsonNode window = body.get("windows").get(0);
        assertEquals(42.0, window.get("revenue").get("Books").asDouble());
        assertEquals(1, window.get("orders").get("Books").asLong());
        assertEquals(0.0, window.get("revenue").get("Home").asDouble());
    }

    @Test
    void servesThePageAndHealth() throws Exception {
        HttpResponse<String> page = get("/");
        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("Trending products"));
        assertEquals(200, get("/healthz").statusCode());
        assertEquals(404, get("/nope").statusCode());
    }

    @Test
    void windowsParameterIsClamped() {
        assertEquals(DashboardServer.DEFAULT_WINDOWS, DashboardServer.windows(URI.create("/api/gmv")));
        assertEquals(1, DashboardServer.windows(URI.create("/api/gmv?windows=-3")));
        assertEquals(RedisKeys.GMV_WINDOWS_KEPT, DashboardServer.windows(URI.create("/api/gmv?windows=99999")));
        assertEquals(DashboardServer.DEFAULT_WINDOWS, DashboardServer.windows(URI.create("/api/gmv?windows=abc")));
    }
}
