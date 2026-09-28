package io.github.tomdong2010.fkr.job;

import io.github.tomdong2010.fkr.model.CategoryRevenue;
import io.github.tomdong2010.fkr.model.ProductScore;
import io.github.tomdong2010.fkr.model.TopProducts;
import io.github.tomdong2010.fkr.model.UserEvent;
import org.apache.flink.api.common.eventtime.Watermark;
import org.apache.flink.api.common.eventtime.WatermarkGenerator;
import org.apache.flink.api.common.eventtime.WatermarkOutput;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.test.junit5.MiniClusterExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs the real pipeline on a local Flink mini cluster with a bounded, hand-made input. */
class TrendingJobTest {
    @RegisterExtension
    static final MiniClusterExtension FLINK = new MiniClusterExtension(
            new MiniClusterResourceConfiguration.Builder()
                    .setNumberTaskManagers(1)
                    .setNumberSlotsPerTaskManager(2)
                    .build());

    private static final long T0 = 1_700_000_000_000L; // aligned to a 10s boundary

    private static UserEvent event(String product, String category, String type, double price, long offsetMs) {
        return new UserEvent("e" + offsetMs + product + type, "u1", product, category, type, price, T0 + offsetMs);
    }

    private static DataStream<UserEvent> source(StreamExecutionEnvironment env, List<UserEvent> events) {
        return env.fromData(events).assignTimestampsAndWatermarks(TrendingJob.watermarks(Duration.ofSeconds(5)));
    }

    @Test
    void ranksProductsByWeightedScorePerWindow() throws Exception {
        List<UserEvent> events = new ArrayList<>();
        // In [T0, T0 + 10s): p1 gets 3 views (3), p2 one purchase (5), p3 one cart (3).
        events.add(event("p1", "Books", UserEvent.VIEW, 10, 1_000));
        events.add(event("p1", "Books", UserEvent.VIEW, 10, 2_000));
        events.add(event("p1", "Books", UserEvent.VIEW, 10, 3_000));
        events.add(event("p2", "Home", UserEvent.PURCHASE, 99, 4_000));
        events.add(event("p3", "Home", UserEvent.CART, 20, 5_000));
        // Shuffled: arrival order must not matter within the out-of-orderness bound.
        Collections.shuffle(events, new Random(1));

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(2);
        List<TopProducts> results = TrendingJob
                .trending(source(env, events), Duration.ofSeconds(10), Duration.ofSeconds(5), 2)
                .executeAndCollect(100);

        Map<Long, TopProducts> byWindow = results.stream().collect(Collectors.toMap(t -> t.windowEnd, t -> t));
        // Window [T0, T0+10s) holds everything; top 2 is p2 (5), then p1 and p3 tie at 3 -> by id.
        TopProducts full = byWindow.get(T0 + 10_000);
        assertArrayEquals(new ProductScore[] {
                new ProductScore("p2", T0 + 10_000, 5),
                new ProductScore("p1", T0 + 10_000, 3),
        }, full.items);
        // Window [T0-5s, T0+5s) sees events before T0+5s: p2 (5), p1 (3); p3 at +5s is outside.
        assertArrayEquals(new ProductScore[] {
                new ProductScore("p2", T0 + 5_000, 5),
                new ProductScore("p1", T0 + 5_000, 3),
        }, byWindow.get(T0 + 5_000).items);
        // Every window that holds data is reported exactly once.
        assertEquals(results.size(), byWindow.size());
    }

    @Test
    void sumsRevenuePerCategoryAndWindow() throws Exception {
        List<UserEvent> events = List.of(
                event("p1", "Books", UserEvent.PURCHASE, 10.10, 1_000),
                event("p2", "Books", UserEvent.PURCHASE, 20.20, 9_999),
                event("p3", "Home", UserEvent.PURCHASE, 5.00, 3_000),
                event("p3", "Home", UserEvent.VIEW, 5.00, 3_000),     // views carry no revenue
                event("p1", "Books", UserEvent.PURCHASE, 1.00, 10_000)); // next window

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(2);
        List<CategoryRevenue> results = TrendingJob.revenue(source(env, events), Duration.ofSeconds(10)).executeAndCollect(100);

        Map<String, CategoryRevenue> byKey = new TreeMap<>();
        for (CategoryRevenue r : results) {
            byKey.put(r.category + "@" + (r.windowStart - T0), r);
        }
        assertEquals(3, byKey.size(), byKey.toString());
        assertEquals(30.30, byKey.get("Books@0").revenue, 1e-9);
        assertEquals(2, byKey.get("Books@0").orders);
        assertEquals(5.00, byKey.get("Home@0").revenue, 1e-9);
        assertEquals(1, byKey.get("Home@0").orders);
        assertEquals(1.00, byKey.get("Books@10000").revenue, 1e-9);
        assertEquals(T0 + 20_000, byKey.get("Books@10000").windowEnd);
    }

    @Test
    void dropsEventsLaterThanTheWatermark() throws Exception {
        List<UserEvent> events = List.of(
                event("p1", "Books", UserEvent.PURCHASE, 10, 1_000),
                // Pushes the watermark to T0 + 25s - 5s = T0 + 20s, closing [T0, T0+10s).
                event("p1", "Books", UserEvent.PURCHASE, 10, 25_000),
                // Belongs to the closed window, so it is dropped as late.
                event("p1", "Books", UserEvent.PURCHASE, 1_000, 2_000));

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        // Same bound as the job, but the watermark advances on every event instead of every
        // 200ms, so the outcome does not depend on timing.
        DataStream<UserEvent> stream = env.fromData(events).assignTimestampsAndWatermarks(
                WatermarkStrategy.<UserEvent>forGenerator(ctx -> new PerEventWatermarks(5_000))
                        .withTimestampAssigner((e, ts) -> e.timestamp));
        List<CategoryRevenue> results = TrendingJob.revenue(stream, Duration.ofSeconds(10)).executeAndCollect(100);

        Optional<CategoryRevenue> first = results.stream().filter(r -> r.windowStart == T0).findFirst();
        assertTrue(first.isPresent());
        assertEquals(10.0, first.get().revenue, 1e-9, "the late 1000.00 purchase must not be counted");
    }

    /** Bounded out-of-orderness, but emitting a watermark after every event. */
    static final class PerEventWatermarks implements WatermarkGenerator<UserEvent> {
        private final long bound;
        private long max = Long.MIN_VALUE / 2;

        PerEventWatermarks(long bound) {
            this.bound = bound;
        }

        @Override
        public void onEvent(UserEvent e, long ts, WatermarkOutput out) {
            max = Math.max(max, e.timestamp);
            out.emitWatermark(new Watermark(max - bound - 1));
        }

        @Override
        public void onPeriodicEmit(WatermarkOutput out) {
        }
    }

    @Test
    void topNKeepsOnlyN() {
        ProductScore[] scores = {
                new ProductScore("b", 1, 5), new ProductScore("a", 1, 5), new ProductScore("c", 1, 9),
        };
        List<ProductScore> sorted = new ArrayList<>(Arrays.asList(scores));
        sorted.sort(TopNFunction.BEST_FIRST);
        assertEquals(List.of("c", "a", "b"), sorted.stream().map(s -> s.productId).collect(Collectors.toList()));
    }
}
