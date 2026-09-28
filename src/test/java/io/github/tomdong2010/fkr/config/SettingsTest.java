package io.github.tomdong2010.fkr.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SettingsTest {
    @Test
    void defaults() {
        Settings s = Settings.of(Map.of());
        assertEquals("localhost:9092", s.kafkaBootstrapServers);
        assertEquals("user-events", s.kafkaTopic);
        assertEquals(10, s.topN);
        assertEquals(Duration.ofSeconds(60), s.trendingWindow);
        assertEquals(Duration.ofSeconds(5), s.trendingSlide);
    }

    @Test
    void legacyVariablesStillWork() {
        Settings s = Settings.of(Map.of("EXAMPLE_KAFKA_SERVER", "kafka:9092", "EXAMPLE_KAFKA_TOPIC", "example"));
        assertEquals("kafka:9092", s.kafkaBootstrapServers);
        assertEquals("example", s.kafkaTopic);
        Settings preferred = Settings.of(Map.of("KAFKA_BOOTSTRAP_SERVERS", "new:9092", "EXAMPLE_KAFKA_SERVER", "old:9092"));
        assertEquals("new:9092", preferred.kafkaBootstrapServers);
    }

    @Test
    void parsesDurations() {
        assertEquals(Duration.ofMillis(500), Settings.parseDuration("500ms"));
        assertEquals(Duration.ofSeconds(5), Settings.parseDuration(" 5S "));
        assertEquals(Duration.ofMinutes(2), Settings.parseDuration("2m"));
        assertEquals(Duration.ofHours(1), Settings.parseDuration("1h"));
        for (String bad : new String[] {"", "5", "five s", "5d"}) {
            assertThrows(IllegalArgumentException.class, () -> Settings.parseDuration(bad), bad);
        }
    }

    @Test
    void rejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> Settings.of(Map.of("TOP_N", "0")));
        assertThrows(IllegalArgumentException.class, () -> Settings.of(Map.of("LATE_EVENT_RATIO", "1.5")));
        assertThrows(IllegalArgumentException.class, () -> Settings.of(Map.of("TRENDING_WINDOW", "5s", "TRENDING_SLIDE", "10s")));
    }
}
