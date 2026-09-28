package io.github.tomdong2010.fkr.model;

import java.util.Objects;

/**
 * A user action on a product. Public fields and a no-argument constructor make it a Flink
 * POJO, so it is serialized efficiently instead of falling back to Kryo.
 */
public class UserEvent {
    /** Event types, from weakest to strongest signal of interest. */
    public static final String VIEW = "view";
    public static final String CART = "cart";
    public static final String PURCHASE = "purchase";

    public String eventId;
    public String userId;
    public String productId;
    public String category;
    public String type;
    public double price;
    /** Event time in epoch milliseconds, set when the action happened (not when it reached Kafka). */
    public long timestamp;

    public UserEvent() {
    }

    public UserEvent(String eventId, String userId, String productId, String category, String type,
                     double price, long timestamp) {
        this.eventId = eventId;
        this.userId = userId;
        this.productId = productId;
        this.category = category;
        this.type = type;
        this.price = price;
        this.timestamp = timestamp;
    }

    /** How strongly this event signals interest in the product: view 1, cart 3, purchase 5. */
    public long popularityScore() {
        if (PURCHASE.equals(type)) {
            return 5;
        }
        if (CART.equals(type)) {
            return 3;
        }
        return 1;
    }

    /** Whether the event carries every field the pipeline relies on. */
    public boolean isValid() {
        return productId != null && category != null && timestamp > 0
                && (VIEW.equals(type) || CART.equals(type) || PURCHASE.equals(type));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserEvent)) {
            return false;
        }
        UserEvent e = (UserEvent) o;
        return Double.compare(e.price, price) == 0 && timestamp == e.timestamp
                && Objects.equals(eventId, e.eventId) && Objects.equals(userId, e.userId)
                && Objects.equals(productId, e.productId) && Objects.equals(category, e.category)
                && Objects.equals(type, e.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, userId, productId, category, type, price, timestamp);
    }

    @Override
    public String toString() {
        return "UserEvent{" + type + " " + productId + " by " + userId + " @" + timestamp + "}";
    }
}
