package io.github.tomdong2010.fkr;

import io.github.tomdong2010.fkr.config.Settings;
import io.github.tomdong2010.fkr.dashboard.DashboardServer;
import io.github.tomdong2010.fkr.job.TrendingJob;
import io.github.tomdong2010.fkr.producer.EventProducer;

import java.util.Locale;

/**
 * Entry point for the non-Flink parts. The mode comes from the first argument or {@code APP_MODE}:
 * {@code producer}, {@code dashboard} or {@code job} (runs the Flink job in a local mini cluster).
 */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : System.getenv().getOrDefault("APP_MODE", "");
        Settings settings = Settings.fromEnv();
        switch (mode.toLowerCase(Locale.ROOT)) {
            case "producer":
                EventProducer.run(settings);
                break;
            case "dashboard":
                DashboardServer.run(settings);
                break;
            case "job":
                TrendingJob.run(settings);
                break;
            default:
                System.err.println("usage: Main producer|dashboard|job   (or set APP_MODE)");
                System.exit(2);
        }
    }
}
