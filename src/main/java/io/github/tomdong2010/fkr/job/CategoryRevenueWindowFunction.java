package io.github.tomdong2010.fkr.job;

import io.github.tomdong2010.fkr.model.CategoryRevenue;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

/** Attaches the category and the window bounds to the aggregated revenue. */
public class CategoryRevenueWindowFunction
        extends ProcessWindowFunction<Tuple2<Double, Long>, CategoryRevenue, String, TimeWindow> {
    @Override
    public void process(String category, Context context, Iterable<Tuple2<Double, Long>> totals,
                        Collector<CategoryRevenue> out) {
        Tuple2<Double, Long> t = totals.iterator().next();
        // Rounded to cents so repeated floating-point sums do not show up as 1234.5600000001.
        double revenue = Math.round(t.f0 * 100) / 100.0;
        out.collect(new CategoryRevenue(category, context.window().getStart(), context.window().getEnd(), revenue, t.f1));
    }
}
