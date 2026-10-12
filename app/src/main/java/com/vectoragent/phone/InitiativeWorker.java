package com.vectoragent.phone;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/**
 * Periodically inspects local memory and notifies about a concrete unresolved item.
 * The worker never changes factual memory or executes external actions.
 */
public final class InitiativeWorker extends Worker {
    private static final String CHANNEL = "astra_initiative";
    private static final long REPEAT_AFTER_MS = 7L * 24L * 60L * 60L * 1000L;

    public InitiativeWorker(Context context, WorkerParameters params) { super(context, params); }

    @Override public Result doWork() {
        Context context = getApplicationContext();
        InitiativeEngine.Report report = InitiativeEngine.scan(context);
        if (!report.actionable) return Result.success();

        android.content.SharedPreferences prefs = context.getSharedPreferences(
                "vector_initiative", Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        String previous = prefs.getString("last_notified_fingerprint", "");
        long lastTime = prefs.getLong("last_notified_at", 0L);
        boolean changed = !report.fingerprint.equals(previous);
        boolean reminderDue = now - lastTime >= REPEAT_AFTER_MS;
        if (changed || reminderDue) {
            boolean posted = postNotification(context, report);
            // Avoid spamming if notification permission is denied; still show latest report in the app.
            if (posted) prefs.edit().putString("last_notified_fingerprint", report.fingerprint)
                    .putLong("last_notified_at", now).apply();
        }
        return Result.success();
    }

    private boolean postNotification(Context context, InitiativeEngine.Report report) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return false;

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return false;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "Astra: инициативы",
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Новые нерешённые задачи, обнаруженные в локальной памяти");
            manager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(context, InitiativeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(context, 731, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL)
                : new Notification.Builder(context);
        Notification notification = builder
                .setSmallIcon(android.R.drawable.ic_menu_search)
                .setContentTitle("Astra нашла нерешённую задачу")
                .setContentText(report.title)
                .setStyle(new Notification.BigTextStyle().bigText(report.nextStep))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build();
        manager.notify(731, notification);
        return true;
    }
}
