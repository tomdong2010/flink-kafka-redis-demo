package io.github.tomdong2010.fkr.producer;

import io.github.tomdong2010.fkr.model.Catalog;
import io.github.tomdong2010.fkr.model.UserEvent;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventGeneratorTest {
    @Test
    void producesValidCatalogEvents() {
        AtomicLong clock = new AtomicLong(1_700_000_000_000L);
        EventGenerator gen = new EventGenerator(42, clock::get, 0);
        Map<String, Integer> types = new HashMap<>();
        for (int i = 0; i < 5_000; i++) {
            UserEvent e = gen.next();
            assertTrue(e.hasRequiredFields(), e.toString());
            Catalog.Product p = Catalog.byId().get(e.productId);
            assertNotNull(p);
            assertEquals(p.category, e.category);
            assertEquals(p.price, e.price);
            assertEquals(clock.get(), e.timestamp, "no late events when the ratio is 0");
            types.merge(e.type, 1, Integer::sum);
            clock.addAndGet(10);
        }
        // Views dominate, purchases are rare but present.
        assertTrue(types.get(UserEvent.VIEW) > types.get(UserEvent.CART));
        assertTrue(types.get(UserEvent.CART) > types.get(UserEvent.PURCHASE));
        assertTrue(types.get(UserEvent.PURCHASE) > 0);
    }

    @Test
    void lateEventsCarryOlderTimestamps() {
        AtomicLong clock = new AtomicLong(1_700_000_000_000L);
        EventGenerator gen = new EventGenerator(7, clock::get, 0.5);
        int late = 0;
        for (int i = 0; i < 2_000; i++) {
            UserEvent e = gen.next();
            assertTrue(e.timestamp <= clock.get());
            assertTrue(clock.get() - e.timestamp <= 15_000);
            if (e.timestamp < clock.get()) {
                late++;
            }
        }
        assertTrue(late > 800 && late < 1_200, "about half should be late, got " + late);
    }

    @Test
    void isDeterministicForASeed() {
        AtomicLong c1 = new AtomicLong(1_000), c2 = new AtomicLong(1_000);
        EventGenerator a = new EventGenerator(1, c1::get, 0.1), b = new EventGenerator(1, c2::get, 0.1);
        for (int i = 0; i < 100; i++) {
            assertEquals(a.next(), b.next());
        }
    }

    @Test
    void flashSalesMoveAround() {
        AtomicLong clock = new AtomicLong(0);
        EventGenerator gen = new EventGenerator(3, clock::get, 0);
        gen.next();
        String first = gen.hottestProductId();
        assertNotNull(first);
        String later = first;
        for (int sale = 0; sale < 10 && later.equals(first); sale++) {
            clock.addAndGet(EventGenerator.FLASH_SALE_EVERY_MS);
            gen.next();
            later = gen.hottestProductId();
        }
        assertNotEquals(first, later, "a new flash sale should eventually take over");
    }
}
