package com.vectoragent.phone;

import android.content.Context;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;

/** User-controlled schedule for bounded, local initiative scans. */
public final class InitiativeScheduler {
    public static final String UNIQUE_PERIODIC = "astra_initiative_daily";
    private InitiativeScheduler() {}

    public static void enable(Context context) {
        Context app = context.getApplicationContext();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                InitiativeWorker.class, 24, TimeUnit.HOURS).build();
        WorkManager.getInstance(app).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request);
        app.getSharedPreferences("vector_initiative", Context.MODE_PRIVATE)
                .edit().putBoolean("enabled", true).apply();
    }

    public static void disable(Context context) {
        Context app = context.getApplicationContext();
        WorkManager.getInstance(app).cancelUniqueWork(UNIQUE_PERIODIC);
        app.getSharedPreferences("vector_initiative", Context.MODE_PRIVATE)
                .edit().putBoolean("enabled", false).apply();
    }

    public static void runNow(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(InitiativeWorker.class).build();
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(
                "astra_initiative_now", ExistingWorkPolicy.REPLACE, request);
    }

    public static boolean isEnabled(Context context) {
        return context.getApplicationContext().getSharedPreferences(
                "vector_initiative", Context.MODE_PRIVATE).getBoolean("enabled", false);
    }
}
