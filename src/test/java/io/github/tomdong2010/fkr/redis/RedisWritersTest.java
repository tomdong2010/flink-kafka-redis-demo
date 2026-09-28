package io.github.tomdong2010.fkr.redis;

import io.github.tomdong2010.fkr.model.CategoryRevenue;
import io.github.tomdong2010.fkr.model.ProductScore;
import io.github.tomdong2010.fkr.model.TopProducts;
import org.apache.flink.metrics.SimpleCounter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.resps.Tuple;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.github.tomdong2010.fkr.redis.RedisTestSupport.REDIS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class RedisWritersTest {
    private Jedis jedis;

    @BeforeAll
    static void startRedis() {
        RedisTestSupport.start();
    }

    @BeforeEach
    void setUp() {
        jedis = new Jedis(REDIS.getHost(), REDIS.getFirstMappedPort());
        jedis.flushAll();
    }

    private <T> void writeAll(RedisWriter<T> writer, List<T> values) throws Exception {
        SimpleCounter counter = new SimpleCounter();
        try (RedisSink.Writer<T> w = new RedisSink.Writer<>(new Jedis(REDIS.getHost(), REDIS.getFirstMappedPort()), writer, counter)) {
            for (T v : values) {
                w.write(v, null);
            }
            w.flush(false);
        }
        assertEquals(values.size(), counter.getCount());
    }

    private static TopProducts top(long windowEnd, Object... idAndScore) {
        ProductScore[] items = new ProductScore[idAndScore.length / 2];
        for (int i = 0; i < items.length; i++) {
            items[i] = new ProductScore((String) idAndScore[2 * i], windowEnd, (Long) idAndScore[2 * i + 1]);
        }
        return new TopProducts(windowEnd, items);
    }

    private List<String> leaderboard() {
        return jedis.zrevrangeWithScores(RedisKeys.TRENDING, 0, -1).stream()
                .map(t -> t.getElement() + "=" + (long) t.getScore()).collect(Collectors.toList());
    }

    @Test
    void leaderboardIsReplacedByNewerWindows() throws Exception {
        writeAll(new TrendingWriter(), List.of(top(1_000, "a", 5L, "b", 3L), top(2_000, "c", 9L)));
        assertEquals(List.of("c=9"), leaderboard(), "products missing from the newer window are removed");
        assertEquals("2000", jedis.get(RedisKeys.TRENDING_WINDOW_END));
    }

    @Test
    void olderWindowsNeverOverwriteNewerOnes() throws Exception {
        // E.g. a replay after failover, or parallel subtasks finishing out of order.
        writeAll(new TrendingWriter(), List.of(top(2_000, "new", 7L), top(1_000, "old", 99L)));
        assertEquals(List.of("new=7"), leaderboard());
        assertEquals("2000", jedis.get(RedisKeys.TRENDING_WINDOW_END));
    }

    @Test
    void rewritingTheSameWindowIsIdempotent() throws Exception {
        TopProducts t = top(3_000, "a", 4L, "b", 2L);
        writeAll(new TrendingWriter(), List.of(t, t, t));
        assertEquals(List.of("a=4", "b=2"), leaderboard());
    }

    @Test
    void revenueIsStoredPerWindowAndIdempotent() throws Exception {
        CategoryRevenue books = new CategoryRevenue("Books", 10_000, 20_000, 30.3, 2);
        CategoryRevenue home = new CategoryRevenue("Home", 10_000, 20_000, 5.0, 1);
        writeAll(new RevenueWriter(), List.of(books, home, books));

        Map<String, String> hash = jedis.hgetAll(RedisKeys.gmvWindow(10_000));
        assertEquals(Map.of("Books", "30.3", "Books:orders", "2", "Home", "5.0", "Home:orders", "1"), hash);
        assertEquals(List.of("10000"), jedis.zrange(RedisKeys.GMV_WINDOWS, 0, -1));
        long ttl = jedis.ttl(RedisKeys.gmvWindow(10_000));
        assertTrue(ttl > 0 && ttl <= RedisKeys.GMV_TTL_SECONDS);
    }

    @Test
    void failedCommandsFailTheFlush() throws Exception {
        // E.g. a script error or a wrong type: the sink must not report the records as written.
        jedis.hset(RedisKeys.TRENDING_WINDOW_END, "not", "a string"); // GET in the script fails
        SimpleCounter counter = new SimpleCounter();
        RedisSink.Writer<TopProducts> w = new RedisSink.Writer<>(
                new Jedis(REDIS.getHost(), REDIS.getFirstMappedPort()), new TrendingWriter(), counter);
        w.write(top(1_000, "a", 1L), null);
        IOException e = assertThrows(IOException.class, () -> w.flush(false));
        assertTrue(e.getCause().getMessage().contains("WRONGTYPE"), e.getCause().getMessage());
        assertEquals(0, counter.getCount());
        w.close();
    }

    @Test
    void onlyTheLatestRevenueWindowsAreKept() throws Exception {
        List<CategoryRevenue> many = new java.util.ArrayList<>();
        for (int i = 0; i < RedisKeys.GMV_WINDOWS_KEPT + 5; i++) {
            many.add(new CategoryRevenue("Books", i * 10_000L, (i + 1) * 10_000L, 1.0, 1));
        }
        writeAll(new RevenueWriter(), many);
        List<Tuple> windows = jedis.zrangeWithScores(RedisKeys.GMV_WINDOWS, 0, -1);
        assertEquals(RedisKeys.GMV_WINDOWS_KEPT, windows.size());
        assertEquals(50_000, (long) windows.get(0).getScore());
    }
}
