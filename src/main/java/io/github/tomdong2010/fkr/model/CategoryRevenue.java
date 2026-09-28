package io.github.tomdong2010.fkr.model;

/** Revenue and order count of one category in a tumbling window. */
public class CategoryRevenue {
    public String category;
    public long windowStart;
    public long windowEnd;
    public double revenue;
    public long orders;

    public CategoryRevenue() {
    }

    public CategoryRevenue(String category, long windowStart, long windowEnd, double revenue, long orders) {
        this.category = category;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.revenue = revenue;
        this.orders = orders;
    }

    @Override
    public String toString() {
        return "CategoryRevenue{" + category + " [" + windowStart + "," + windowEnd + ") " + revenue + " / " + orders + "}";
    }
}
