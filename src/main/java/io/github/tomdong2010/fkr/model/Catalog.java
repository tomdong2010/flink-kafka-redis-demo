package io.github.tomdong2010.fkr.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The simulated product catalog, shared by the producer and the dashboard. */
public final class Catalog {
    /** A product for sale. */
    public static final class Product {
        public final String id;
        public final String name;
        public final String category;
        public final double price;
        /** Relative baseline popularity. */
        public final double weight;

        Product(String id, String name, String category, double price, double weight) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.price = price;
            this.weight = weight;
        }
    }

    private static final Map<String, Product> PRODUCTS = new LinkedHashMap<>();

    static {
        add("p01", "Mechanical Keyboard", "Electronics", 89.00, 6);
        add("p02", "Noise-Cancelling Headphones", "Electronics", 249.00, 8);
        add("p03", "4K Monitor", "Electronics", 329.00, 4);
        add("p04", "Wireless Mouse", "Electronics", 29.90, 7);
        add("p05", "USB-C Hub", "Electronics", 39.50, 5);
        add("p06", "Designing Data-Intensive Applications", "Books", 42.00, 5);
        add("p07", "Stream Processing with Apache Flink", "Books", 55.00, 3);
        add("p08", "Kafka: The Definitive Guide", "Books", 49.00, 4);
        add("p09", "Redis in Action", "Books", 38.00, 2);
        add("p10", "Espresso Machine", "Home", 399.00, 3);
        add("p11", "Air Purifier", "Home", 159.00, 4);
        add("p12", "Standing Desk", "Home", 459.00, 2);
        add("p13", "Robot Vacuum", "Home", 279.00, 5);
        add("p14", "Running Shoes", "Sports", 119.00, 7);
        add("p15", "Yoga Mat", "Sports", 25.00, 5);
        add("p16", "Smart Watch", "Sports", 199.00, 6);
        add("p17", "Carbon Road Bike", "Sports", 1899.00, 1);
        add("p18", "Down Jacket", "Fashion", 189.00, 5);
        add("p19", "Canvas Backpack", "Fashion", 59.00, 6);
        add("p20", "Sunglasses", "Fashion", 79.00, 4);
        add("p21", "Vitamin C Serum", "Beauty", 34.00, 5);
        add("p22", "Electric Toothbrush", "Beauty", 69.00, 4);
        add("p23", "Sunscreen SPF50", "Beauty", 19.90, 6);
        add("p24", "Hair Dryer", "Beauty", 129.00, 3);
    }

    private Catalog() {
    }

    private static void add(String id, String name, String category, double price, double weight) {
        PRODUCTS.put(id, new Product(id, name, category, price, weight));
    }

    public static List<Product> products() {
        return List.copyOf(PRODUCTS.values());
    }

    public static Map<String, Product> byId() {
        return Collections.unmodifiableMap(PRODUCTS);
    }
}
