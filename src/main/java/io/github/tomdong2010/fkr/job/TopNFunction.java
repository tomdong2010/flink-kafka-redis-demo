package io.github.tomdong2010.fkr.job;

import io.github.tomdong2010.fkr.model.ProductScore;
import io.github.tomdong2010.fkr.model.TopProducts;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Collects every product's score for one window (the key is the window end) and emits the top
 * N once the watermark passes the window end, i.e. once all scores for that window have
 * arrived. This is the classic "hot items" pattern.
 */
public class TopNFunction extends KeyedProcessFunction<Long, ProductScore, TopProducts> {
    static final Comparator<ProductScore> BEST_FIRST = Comparator
            .comparingLong((ProductScore p) -> p.score).reversed()
            .thenComparing(p -> p.productId);

    private final int n;
    private transient ListState<ProductScore> scores;

    public TopNFunction(int n) {
        this.n = n;
    }

    @Override
    public void open(OpenContext openContext) {
        scores = getRuntimeContext().getListState(new ListStateDescriptor<>("scores", ProductScore.class));
    }

    @Override
    public void processElement(ProductScore score, Context ctx, Collector<TopProducts> out) throws Exception {
        scores.add(score);
        // All window results carry timestamp windowEnd - 1, so windowEnd fires after all of them.
        ctx.timerService().registerEventTimeTimer(score.windowEnd);
    }

    @Override
    public void onTimer(long timestamp, OnTimerContext ctx, Collector<TopProducts> out) throws Exception {
        List<ProductScore> all = new ArrayList<>();
        for (ProductScore s : scores.get()) {
            all.add(s);
        }
        scores.clear();
        all.sort(BEST_FIRST);
        List<ProductScore> top = all.subList(0, Math.min(n, all.size()));
        out.collect(new TopProducts(ctx.getCurrentKey(), top.toArray(new ProductScore[0])));
    }
}
