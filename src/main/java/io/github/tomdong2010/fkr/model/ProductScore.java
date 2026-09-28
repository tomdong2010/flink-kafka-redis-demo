package io.github.tomdong2010.fkr.model;

import java.util.Objects;

/** Popularity score of one product in the sliding window ending at {@code windowEnd}. */
public class ProductScore {
    public String productId;
    public long windowEnd;
    public long score;

    public ProductScore() {
    }

    public ProductScore(String productId, long windowEnd, long score) {
        this.productId = productId;
        this.windowEnd = windowEnd;
        this.score = score;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ProductScore)) {
            return false;
        }
        ProductScore p = (ProductScore) o;
        return windowEnd == p.windowEnd && score == p.score && Objects.equals(productId, p.productId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId, windowEnd, score);
    }

    @Override
    public String toString() {
        return productId + "=" + score + "@" + windowEnd;
    }
}
