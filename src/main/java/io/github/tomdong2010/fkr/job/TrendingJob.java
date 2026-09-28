package io.github.tomdong2010.fkr.job;

import io.github.tomdong2010.fkr.config.Settings;
import io.github.tomdong2010.fkr.model.CategoryRevenue;
import io.github.tomdong2010.fkr.model.TopProducts;
import io.github.tomdong2010.fkr.model.UserEvent;
import io.github.tomdong2010.fkr.redis.RedisSink;
import io.github.tomdong2010.fkr.redis.RevenueWriter;
import io.github.tomdong2010.fkr.redis.TrendingWriter;
import io.github.tomdong2010.fkr.serde.UserEventDeserializer;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;

import java.time.Duration;

/**
 * Reads user events from Kafka and maintains two real-time views in Redis:
 * <ul>
 *   <li>the top N products by popularity over a sliding window (default 60s, sliding every 5s);</li>
 *   <li>revenue and orders per category over tumbling windows (default 10s).</li>
 * </ul>
 * Both use event time, so out-of-order events land in the right window.
 */
public final class TrendingJob {
    private TrendingJob() {
    }

    public static void main(String[] args) throws Exception {
        run(Settings.fromEnv());
    }

    public static void run(Settings settings) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.enableCheckpointing(settings.checkpointInterval.toMillis());

        KafkaSource<UserEvent> source = KafkaSource.<UserEvent>builder()
                .setBootstrapServers(settings.kafkaBootstrapServers)
                .setTopics(settings.kafkaTopic)
                .setGroupId(settings.kafkaGroupId)
                // Resume from the offsets committed on checkpoints; start at the tail on first run.
                .setStartingOffsets(OffsetsInitializer.committedOffsets(OffsetResetStrategy.LATEST))
                .setDeserializer(new UserEventDeserializer())
                .build();

        DataStream<UserEvent> events = env
                .fromSource(source, watermarks(settings.maxOutOfOrderness), "kafka-user-events")
                .uid("kafka-user-events");

        trending(events, settings.trendingWindow, settings.trendingSlide, settings.topN)
                .sinkTo(new RedisSink<>(settings.redisHost, settings.redisPort, new TrendingWriter()))
                .name("redis-trending").uid("redis-trending");

        revenue(events, settings.gmvWindow)
                .sinkTo(new RedisSink<>(settings.redisHost, settings.redisPort, new RevenueWriter()))
                .name("redis-revenue").uid("redis-revenue");

        env.execute("Trending products & GMV (Kafka -> Flink -> Redis)");
    }

    /**
     * Event time comes from the event itself; events may arrive up to {@code maxOutOfOrderness}
     * late. Idle partitions do not hold back the watermark.
     */
    public static WatermarkStrategy<UserEvent> watermarks(Duration maxOutOfOrderness) {
        return WatermarkStrategy.<UserEvent>forBoundedOutOfOrderness(maxOutOfOrderness)
                .withTimestampAssigner((event, recordTimestamp) -> event.timestamp)
                .withIdleness(Duration.ofSeconds(30));
    }

    /** Top N products by popularity score per sliding window. */
    public static DataStream<TopProducts> trending(DataStream<UserEvent> events, Duration size, Duration slide, int topN) {
        return events
                .keyBy(e -> e.productId)
                .window(SlidingEventTimeWindows.of(size, slide))
                .aggregate(new ScoreAggregate(), new ProductScoreWindowFunction())
                .name("product-scores").uid("product-scores")
                .keyBy(s -> s.windowEnd)
                .process(new TopNFunction(topN))
                .name("top-n").uid("top-n");
    }

    /** Revenue and order count per category per tumbling window. */
    public static DataStream<CategoryRevenue> revenue(DataStream<UserEvent> events, Duration size) {
        return events
                .filter(e -> UserEvent.PURCHASE.equals(e.type))
                .name("purchases").uid("purchases")
                .keyBy(e -> e.category)
                .window(TumblingEventTimeWindows.of(size))
                .aggregate(new RevenueAggregate(), new CategoryRevenueWindowFunction())
                .name("category-revenue").uid("category-revenue");
    }
}
