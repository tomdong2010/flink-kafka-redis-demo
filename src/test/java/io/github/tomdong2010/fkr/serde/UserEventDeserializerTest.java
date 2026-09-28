package io.github.tomdong2010.fkr.serde;

import io.github.tomdong2010.fkr.model.UserEvent;
import org.apache.flink.util.Collector;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UserEventDeserializerTest {
    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void roundTrip() throws Exception {
        UserEvent e = new UserEvent("id", "u1", "p01", "Electronics", UserEvent.PURCHASE, 89.0, 1_700_000_000_000L);
        assertEquals(e, UserEventDeserializer.parse(Json.MAPPER.writeValueAsBytes(e)));
    }

    @Test
    void serializesOnlyTheEventFields() throws Exception {
        UserEvent e = new UserEvent("id", "u1", "p01", "Electronics", UserEvent.VIEW, 89.0, 5);
        Set<String> fields = new HashSet<>();
        Json.MAPPER.readTree(Json.MAPPER.writeValueAsBytes(e)).fieldNames().forEachRemaining(fields::add);
        assertEquals(Set.of("eventId", "userId", "productId", "category", "type", "price", "timestamp"), fields);
    }

    @Test
    void ignoresUnknownFields() {
        UserEvent e = UserEventDeserializer.parse(bytes(
                "{\"productId\":\"p01\",\"category\":\"Books\",\"type\":\"view\",\"timestamp\":5,\"extra\":true}"));
        assertEquals("p01", e.productId);
    }

    @Test
    void rejectsMalformedOrIncompleteRecords() {
        assertNull(UserEventDeserializer.parse(null));
        assertNull(UserEventDeserializer.parse(bytes("not json")));
        assertNull(UserEventDeserializer.parse(bytes("{\"category\":\"Books\",\"type\":\"view\",\"timestamp\":5}")));
        assertNull(UserEventDeserializer.parse(bytes("{\"productId\":\"p01\",\"category\":\"Books\",\"type\":\"like\",\"timestamp\":5}")));
        assertNull(UserEventDeserializer.parse(bytes("{\"productId\":\"p01\",\"category\":\"Books\",\"type\":\"view\"}")));
    }

    @Test
    void skipsPoisonRecordsWithoutFailing() throws Exception {
        List<UserEvent> out = new ArrayList<>();
        Collector<UserEvent> collector = new Collector<>() {
            @Override
            public void collect(UserEvent record) {
                out.add(record);
            }

            @Override
            public void close() {
            }
        };
        UserEventDeserializer d = new UserEventDeserializer();
        d.deserialize(new ConsumerRecord<>("t", 0, 0, null, bytes("{{{")), collector);
        d.deserialize(new ConsumerRecord<>("t", 0, 1, null,
                bytes("{\"productId\":\"p01\",\"category\":\"Books\",\"type\":\"view\",\"timestamp\":5}")), collector);
        assertEquals(1, out.size());
    }
}
