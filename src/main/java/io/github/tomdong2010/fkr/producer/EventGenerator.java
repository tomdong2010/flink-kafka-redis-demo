package io.github.tomdong2010.fkr.producer;

import io.github.tomdong2010.fkr.model.Catalog;
import io.github.tomdong2010.fkr.model.UserEvent;

import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Simulates shoppers. Product popularity follows the catalog weights, and every so often a
 * random product gets a "flash sale" boost that decays over time, so the leaderboard keeps
 * changing. A configurable share of events carries an older timestamp to exercise
 * out-of-order and late-data handling in Flink.
 */
public class EventGenerator {
    /** How often a new flash sale starts, in milliseconds. */
    static final long FLASH_SALE_EVERY_MS = 20_000;
    private static final double FLASH_SALE_BOOST = 12.0;
    /** Share of the boost left after one second. */
    private static final double BOOST_DECAY_PER_SECOND = 0.93;
    /** Late events are delayed by up to this much, so some fall behind the watermark. */
    private static final long MAX_DELAY_MS = 15_000;

    private final List<Catalog.Product> products = Catalog.products();
    private final double[] boost = new double[products.size()];
    private final Random random;
    private final LongSupplier clock;
    private final double lateEventRatio;
    private long lastDecay;
    private long nextFlashSale;

    public EventGenerator(long seed, LongSupplier clock, double lateEventRatio) {
        this.random = new Random(seed);
        this.clock = clock;
        this.lateEventRatio = lateEventRatio;
        this.lastDecay = clock.getAsLong();
        this.nextFlashSale = lastDecay;
    }

    public UserEvent next() {
        long now = clock.getAsLong();
        updateBoosts(now);
        Catalog.Product product = pickProduct();

        double r = random.nextDouble();
        String type = r < 0.85 ? UserEvent.VIEW : r < 0.95 ? UserEvent.CART : UserEvent.PURCHASE;
        long timestamp = random.nextDouble() < lateEventRatio ? now - 1 - (long) (random.nextDouble() * MAX_DELAY_MS) : now;

        return new UserEvent(
                new UUID(random.nextLong(), random.nextLong()).toString(),
                "u" + random.nextInt(5_000),
                product.id,
                product.category,
                type,
                product.price,
                timestamp);
    }

    /** The product currently on flash sale, or {@code null} before the first one. */
    public String hottestProductId() {
        int best = -1;
        for (int i = 0; i < boost.length; i++) {
            if (boost[i] > 0.5 && (best < 0 || boost[i] > boost[best])) {
                best = i;
            }
        }
        return best < 0 ? null : products.get(best).id;
    }

    private void updateBoosts(long now) {
        long elapsed = now - lastDecay;
        if (elapsed >= 1_000) {
            double factor = Math.pow(BOOST_DECAY_PER_SECOND, elapsed / 1_000.0);
            for (int i = 0; i < boost.length; i++) {
                boost[i] *= factor;
            }
            lastDecay = now;
        }
        if (now >= nextFlashSale) {
            boost[random.nextInt(boost.length)] += FLASH_SALE_BOOST;
            nextFlashSale = now + FLASH_SALE_EVERY_MS;
        }
    }

    private Catalog.Product pickProduct() {
        double total = 0;
        for (int i = 0; i < products.size(); i++) {
            total += weight(i);
        }
        double target = random.nextDouble() * total;
        for (int i = 0; i < products.size(); i++) {
            target -= weight(i);
            if (target <= 0) {
                return products.get(i);
            }
        }
        return products.get(products.size() - 1);
    }

    private double weight(int i) {
        return products.get(i).weight * (1 + boost[i]);
    }
}
