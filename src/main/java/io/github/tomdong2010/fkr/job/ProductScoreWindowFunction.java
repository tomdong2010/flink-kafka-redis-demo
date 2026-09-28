package io.github.tomdong2010.fkr.job;

import io.github.tomdong2010.fkr.model.ProductScore;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

/** Attaches the product and the window end to the aggregated score. */
public class ProductScoreWindowFunction extends ProcessWindowFunction<Long, ProductScore, String, TimeWindow> {
    @Override
    public void process(String productId, Context context, Iterable<Long> scores, Collector<ProductScore> out) {
        out.collect(new ProductScore(productId, context.window().getEnd(), scores.iterator().next()));
    }
}
