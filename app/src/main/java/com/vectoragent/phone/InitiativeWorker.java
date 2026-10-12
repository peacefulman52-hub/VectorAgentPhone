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
 * When the user explicitly enables daily research, uses keyless public web search
 * for at most one unresolved task per scheduled run. It never
 * changes MemoryStore facts or executes commands in other apps.
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
        boolean dailyResearchAllowed = prefs.getBoolean("daily_research_consent", false);
        if (dailyResearchAllowed) {
            try {
                MemoryStore store = new MemoryStore(context);
                String topicId = "";
                String title = report.title;
                String question = "";

                if ("RESEARCH".equals(report.taskType)) {
                    InitiativeResearchAgenda.Topic topic = InitiativeResearchAgenda.get(context, report.taskId);
                    if (topic != null && !topic.completed) {
                        topicId = topic.id; title = topic.title; question = topic.question;
                    }
                } else if ("CANDIDATE".equals(report.taskType) || "CONFLICT".equals(report.taskType)) {
                    question = store.researchTarget(report.taskId);
                } else if ("HYPOTHESIS".equals(report.taskType)) {
                    for (MemoryStore.Hypothesis h : store.hypotheses()) {
                        if (report.taskId.equals(h.id)) {
                            question = "Проверить гипотезу: " + h.rule + " Основание: " + h.evidence;
                            break;
                        }
                    }
                }

                if (!question.trim().isEmpty()
                        && !InitiativeResearchAgenda.hasSuccessfulResearchForQuestion(context, question)) {
                    FreeWebResearch.Result result = FreeWebResearch.research(question);
                    if (result.report != null && !result.report.trim().isEmpty()) {
                        ResearchMemoryBridge.Summary bridge = ResearchMemoryBridge.ingest(store, question, result.report);
                        InitiativeResearchAgenda.saveResult(context, topicId, title, question, result.report, result.urls);
                        // Persist preliminary findings from background research into the same local clusters.
                        ResearchKnowledgeStore.ingestReport(context, title, question, result.report, result.urls);
                        String summary = result.report.length() > 650
                                ? result.report.substring(0, 650) + "…" : result.report;
                        postMessage(context, "Astra завершила исследование", title + "\n\n" + summary
                                + "\n\n" + bridge.toDisplayString()
                                + "\n\nПолный отчёт и источники сохранены в Astra Initiative. Выводы в памяти остаются предварительными.");
                        InitiativeEngine.scan(context);
                        return Result.success();
                    }
                    postMessage(context, "Astra: пустой ответ исследования",
                            title + "\nВеб-поиск не вернул текста. Задача не отмечена завершённой.");
                    return Result.success();
                }
            } catch (Exception e) {
                postMessage(context, "Astra: исследование не удалось",
                        report.title + "\n" + safeMessage(e)
                        + "\nЗадача не помечена выполненной; попробуй исследовать её вручную.");
                return Result.success();
            }
        }

        String fingerprint = report.fingerprint;
        long now = System.currentTimeMillis();
        String previous = prefs.getString("last_notified_fingerprint", "");
        long lastTime = prefs.getLong("last_notified_at", 0L);
        boolean changed = !fingerprint.equals(previous);
        boolean reminderDue = now - lastTime >= REPEAT_AFTER_MS;
        if (changed || reminderDue) {
            String note = report.displayText();
            if ("RESEARCH".equals(report.taskType) && !dailyResearchAllowed) {
                note += "\n\nФоновое веб-исследование не разрешено. Включи его в Astra Initiative или запусти исследование вручную.";
            }
            boolean posted = postMessage(context,
                    "Astra выбрала следующую задачу: " + report.title, note);
            if (posted) prefs.edit().putString("last_notified_fingerprint", fingerprint)
                    .putLong("last_notified_at", now).apply();
        }
        return Result.success();
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) return e.getClass().getSimpleName();
        // Avoid dumping large HTTP bodies, request headers or secrets into Android notifications.
        message = message.replaceAll("(?i)Bearer\\s+[^\\s]+", "Bearer [redacted]");
        return message.length() > 240 ? message.substring(0, 240) + "…" : message;
    }

    private boolean postMessage(Context context, String title, String message) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return false;

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return false;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "Astra: инициативы",
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Исследовательские задачи и результаты Astra");
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
                .setContentTitle(title)
                .setContentText(message.length() > 220 ? message.substring(0, 220) + "…" : message)
                .setStyle(new Notification.BigTextStyle().bigText(message))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build();
        manager.notify(731, notification);
        return true;
    }
}
