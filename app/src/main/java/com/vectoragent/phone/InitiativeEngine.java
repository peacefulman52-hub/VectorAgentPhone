package com.vectoragent.phone;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Builds a persistent, evidence-aware queue of the next useful research action.
 * It does not promote facts, resolve conflicts, or change MemoryStore state.
 */
public final class InitiativeEngine {
    private static final String PREFS = "vector_initiative";
    private static final String REPORT_KEY = "latest_report";

    private InitiativeEngine() {}

    private static final class Task {
        String type, id, title, reason, nextStep, fingerprint;
        int priority;
        long created;
    }

    public static final class Report {
        public final boolean actionable;
        public final String taskType, taskId, fingerprint, title, reason, nextStep;
        public final int activeCount, candidateCount, conflictCount, rejectedCount, hypothesisCount;
        public final long scannedAt;
        Report(boolean actionable, String taskType, String taskId, String fingerprint,
               String title, String reason, String nextStep, int activeCount, int candidateCount,
               int conflictCount, int rejectedCount, int hypothesisCount, long scannedAt) {
            this.actionable = actionable; this.taskType = taskType; this.taskId = taskId;
            this.fingerprint = fingerprint; this.title = title; this.reason = reason;
            this.nextStep = nextStep; this.activeCount = activeCount; this.candidateCount = candidateCount;
            this.conflictCount = conflictCount; this.rejectedCount = rejectedCount;
            this.hypothesisCount = hypothesisCount; this.scannedAt = scannedAt;
        }
        public String displayText() {
            StringBuilder out = new StringBuilder();
            out.append(actionable ? "СЛЕДУЮЩАЯ ИНИЦИАТИВА" : "НОВЫХ ЗАДАЧ НЕТ").append("\n\n");
            out.append(title).append("\n\n").append(reason).append("\n\nСледующий шаг: ").append(nextStep);
            out.append("\n\nСостояние памяти: ACTIVE ").append(activeCount)
               .append(" · CANDIDATE ").append(candidateCount)
               .append(" · CONFLICT ").append(conflictCount)
               .append(" · REJECTED ").append(rejectedCount)
               .append(" · гипотез ").append(hypothesisCount);
            out.append("\nПроверено: ").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.ROOT)
                    .format(new java.util.Date(scannedAt)));
            return out.toString();
        }
        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("actionable", actionable); o.put("taskType", taskType); o.put("taskId", taskId);
            o.put("fingerprint", fingerprint); o.put("title", title); o.put("reason", reason);
            o.put("nextStep", nextStep); o.put("activeCount", activeCount);
            o.put("candidateCount", candidateCount); o.put("conflictCount", conflictCount);
            o.put("rejectedCount", rejectedCount); o.put("hypothesisCount", hypothesisCount);
            o.put("scannedAt", scannedAt);
            return o;
        }
        static Report fromJson(String raw) throws Exception {
            JSONObject o = new JSONObject(raw);
            return new Report(o.optBoolean("actionable"), o.optString("taskType"), o.optString("taskId"),
                    o.optString("fingerprint"), o.optString("title"), o.optString("reason"),
                    o.optString("nextStep"), o.optInt("activeCount"), o.optInt("candidateCount"),
                    o.optInt("conflictCount"), o.optInt("rejectedCount"), o.optInt("hypothesisCount"),
                    o.optLong("scannedAt"));
        }
    }

    public static synchronized Report scan(Context context) {
        Context app = context.getApplicationContext();
        MemoryStore store = new MemoryStore(app);
        List<MemoryStore.Item> items = store.all();
        List<Task> tasks = new ArrayList<>();
        int active = 0, candidate = 0, conflict = 0, rejected = 0;

        for (MemoryStore.Item item : items) {
            if ("ACTIVE".equals(item.status)) active++;
            else if ("CANDIDATE".equals(item.status)) candidate++;
            else if ("CONFLICT".equals(item.status)) conflict++;
            else if ("REJECTED".equals(item.status)) rejected++;

            if ("CONFLICT".equals(item.status)
                    && !InitiativeResearchAgenda.hasSuccessfulResearchForQuestion(app, item.text)) {
                Task t = new Task();
                t.type = "CONFLICT"; t.id = item.id; t.priority = 100; t.created = item.created;
                t.title = "Проверить противоречие";
                t.reason = "В памяти сохранены конфликтующие сведения: «" + item.text + "». Автоматически выбирать одну сторону нельзя.";
                t.nextStep = "Найти независимое свидетельство по этому утверждению. Сохранить обе версии до появления достаточных доказательств.";
                t.fingerprint = t.type + "|" + item.id + "|" + item.updated + "|" + item.status;
                tasks.add(t);
            } else if ("CANDIDATE".equals(item.status)
                    && !InitiativeResearchAgenda.hasSuccessfulResearchForQuestion(app, item.text)) {
                Task t = new Task();
                t.type = "CANDIDATE"; t.id = item.id; t.priority = 70; t.created = item.created;
                t.title = "Проверить новое утверждение";
                t.reason = "Есть неподтверждённый кандидат: «" + item.text + "». Его статус ещё не означает, что он истинный.";
                t.nextStep = "Проверить источник и поискать независимое подтверждение. Не повышать статус из-за повторения той же информации.";
                t.fingerprint = t.type + "|" + item.id + "|" + item.updated + "|" + item.status;
                tasks.add(t);
            }
        }

        int hypothesisCount = 0;
        for (MemoryStore.Hypothesis h : store.hypotheses()) {
            hypothesisCount++;
            String hypothesisQuestion = "Проверить гипотезу: " + h.rule + " Основание: " + h.evidence;
            if (!"REJECTED".equals(h.status) && !"ACTIVE".equals(h.status)
                    && !InitiativeResearchAgenda.hasSuccessfulResearchForQuestion(app, hypothesisQuestion)) {
                Task t = new Task();
                t.type = "HYPOTHESIS"; t.id = h.id; t.priority = 60; t.created = 0;
                t.title = "Провести различающий эксперимент";
                t.reason = "Осталась непроверенная гипотеза: «" + h.rule + "». Основание: " + h.evidence;
                t.nextStep = hypothesisQuestion;
                t.fingerprint = t.type + "|" + h.id + "|" + h.version + "|" + h.support + "|" + h.status;
                tasks.add(t);
            }
        }

        Collections.sort(tasks, new Comparator<Task>() {
            @Override public int compare(Task a, Task b) {
                int p = Integer.compare(b.priority, a.priority);
                if (p != 0) return p;
                return Long.compare(a.created, b.created);
            }
        });

        // A clean install still gets a concrete, bounded research goal instead of an empty queue.
        // Memory conflicts/candidates/hypotheses keep priority over the starter research programme.
        InitiativeResearchAgenda.Topic topic = InitiativeResearchAgenda.next(app);
        if (topic != null) {
            Task t = new Task();
            t.type = "RESEARCH"; t.id = topic.id; t.priority = 40; t.created = topic.order;
            t.title = "Исследовать: " + topic.title;
            t.reason = topic.goal;
            t.nextStep = topic.question;
            t.fingerprint = "RESEARCH|" + topic.id + "|" + topic.updatedAt + "|pending";
            tasks.add(t);
        }

        Collections.sort(tasks, new Comparator<Task>() {
            @Override public int compare(Task a, Task b) {
                int p = Integer.compare(b.priority, a.priority);
                if (p != 0) return p;
                return Long.compare(a.created, b.created);
            }
        });

        Task best = tasks.isEmpty() ? null : tasks.get(0);
        long now = System.currentTimeMillis();
        Report report;
        if (best != null) {
            report = new Report(true, best.type, best.id, best.fingerprint, best.title,
                    best.reason, best.nextStep, active, candidate, conflict, rejected, hypothesisCount, now);
        } else {
            report = new Report(false, "NONE", "", "NONE|" + active + "|" + candidate + "|" + conflict + "|" + rejected,
                    "Стартовая программа исследований завершена",
                    "Все подготовленные вопросы исследованы, и сейчас нет незакрытых конфликтов, кандидатов или гипотез.",
                    "Добавь новый исследовательский вопрос. Не создавай искусственную задачу только ради активности.",
                    active, candidate, conflict, rejected, hypothesisCount, now);
        }

        try {
            SharedPreferences p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            p.edit().putString(REPORT_KEY, report.toJson().toString())
                    .putLong("last_scan_at", now).putInt("scan_count", p.getInt("scan_count", 0) + 1).apply();
        } catch (Exception ignored) {}
        return report;
    }

    public static Report readLatest(Context context) {
        SharedPreferences p = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try { return Report.fromJson(p.getString(REPORT_KEY, "{}")); }
        catch (Exception ignored) { return null; }
    }
}
