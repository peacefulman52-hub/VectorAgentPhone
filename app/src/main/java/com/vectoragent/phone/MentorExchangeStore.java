package com.vectoragent.phone;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Local, no-API-key handoff between Astra and a human-operated ChatGPT session.
 * Mentor replies are saved as review notes and preliminary conclusions only:
 * they are never written into factual MemoryStore or VERIFIED_FACTS.md automatically.
 */
public final class MentorExchangeStore {
    private static final String DIR = "mentor-memory";
    private static final String CHECKPOINT = "CHECKPOINT.md";
    private static final String DECISIONS = "DECISIONS.md";
    private static final String EVENTS = "EVENTS.jsonl";
    private static final String VERIFIED = "VERIFIED_FACTS.md";
    private static final String LATEST_REPLY = "LATEST_MENTOR_REPLY.md";

    private MentorExchangeStore() {}

    static File storageDir(Context context) {
        File dir = new File(context.getApplicationContext().getFilesDir(), DIR);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    static File file(Context context, String name) {
        return new File(storageDir(context), name);
    }

    private static File repliesDir(Context context) {
        File dir = new File(storageDir(context), "replies");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static synchronized void ensureFiles(Context context) {
        writeIfMissing(file(context, CHECKPOINT),
                "# CHECKPOINT.md\n\nNo handoff checkpoint has been prepared yet.\n");
        writeIfMissing(file(context, DECISIONS),
                "# DECISIONS.md\n\nOnly decisions explicitly confirmed by the user belong here. Mentor advice alone is not a decision.\n");
        writeIfMissing(file(context, EVENTS), "");
        writeIfMissing(file(context, VERIFIED),
                "# VERIFIED_FACTS.md\n\nNo facts are added automatically. Add an item only after checking its primary sources and recording the evidence. A mentor reply is not a verified fact.\n");
    }

    public static synchronized String buildMentorPrompt(Context context) {
        ensureFiles(context);
        String latest = InitiativeResearchAgenda.latestResultText(context);
        String clusters = ResearchKnowledgeStore.summaryText(context);
        updateCheckpoint(context, "Пакет контекста подготовлен для ручной проверки наставником", latest);

        StringBuilder prompt = new StringBuilder();
        prompt.append("ЗАДАЧА: ты выступаешь наставником и проверяющим для локального Android-агента Astra.\n")
                .append("Это ручной обмен текстом; у тебя нет прямого доступа к приложению и ты не должен заявлять, что изменил его.\n")
                .append("Проверь данные критически. Разделяй проверенные сведения, предварительные наблюдения, гипотезы и собственные предположения. ")
                .append("Не считай совпадение страниц независимым подтверждением, если они могут копировать один источник. ")
                .append("Не выдумывай открытые страницы, цитаты, ссылки или результаты тестов. ")
                .append("Не превращай свой ответ в факт приложения: Astra сохранит его как предварительную заметку, пока пользователь отдельно не проверит выводы.\n\n")
                .append("ВЕРНИ ОТВЕТ В СТРУКТУРЕ:\n")
                .append("1. Что в отчёте обосновано, а что пока не доказано.\n")
                .append("2. Ошибки, противоречия и возможные зависимости источников.\n")
                .append("3. Что нужно проверить по первоисточникам (с конкретными URL, если они известны).\n")
                .append("4. Один ближайший эксперимент для Astra: действие, ожидаемый результат и критерий успеха/неуспеха.\n")
                .append("5. Какие изменения памяти предложить: тип записи, краткое содержание, основание и статус проверки. Не предлагай автоматическую запись в VERIFIED_FACTS.\n\n")
                .append("=== CHECKPOINT ===\n").append(readTail(file(context, CHECKPOINT), 2200))
                .append("\n\n=== PREVIOUS DECISIONS (user-confirmed only) ===\n").append(readTail(file(context, DECISIONS), 1800))
                .append("\n\n=== RECENT EVENTS ===\n").append(readTail(file(context, EVENTS), 2600))
                .append("\n\n=== LATEST ASTRA RESEARCH REPORT ===\n").append(limit(latest, 10500))
                .append("\n\n=== PRELIMINARY KNOWLEDGE CLUSTERS (not proof) ===\n").append(limit(clusters, 6500))
                .append("\n\nКонец пакета. Если контекста недостаточно, явно перечисли, чего не хватает, вместо догадок.");
        String result = prompt.toString();
        appendEvent(context, "MENTOR_PACKAGE_PREPARED", "Создан текстовый пакет для ручной передачи наставнику",
                "reportChars=" + latest.length() + "; promptChars=" + result.length());
        return result;
    }

    public static synchronized String importMentorReply(Context context, String reply, boolean userConfirmedDecision) {
        ensureFiles(context);
        String text = reply == null ? "" : reply.trim();
        if (text.length() < 20) return "Ответ слишком короткий: вставь полный разбор наставника (не менее 20 символов).";

        long now = System.currentTimeMillis();
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date(now));
        String content = "# Ответ наставника — " + stamp + "\n\n"
                + "Статус: PRELIMINARY / НЕ ПРОВЕРЕНО. Источник: ручной обмен с ChatGPT.\n\n"
                + "Важно: этот текст сам по себе не является независимым доказательством и не изменяет фактическую память.\n\n"
                + text + "\n";
        File savedReply = new File(repliesDir(context), "mentor-reply-" + stamp + ".md");
        if (!write(savedReply, content) || !write(file(context, LATEST_REPLY), content)) {
            return "Не удалось сохранить ответ наставника в локальную память приложения.";
        }

        // Keep a short searchable note in the research graph, explicitly marked as derived
        // mentor feedback. No URLs are attributed to it, and factual MemoryStore is untouched.
        String indexed = "ПРЕДВАРИТЕЛЬНАЯ ОБРАТНАЯ СВЯЗЬ НАСТАВНИКА (НЕ ФАКТ):\n"
                + limit(text, 2600);
        int added = ResearchKnowledgeStore.addPreliminaryConclusion(
                context,
                indexed,
                "Обратная связь наставника",
                "Критическая проверка последнего исследования Astra",
                "Ручной ответ ChatGPT. Не является независимым источником; проверить конкретные утверждения и URL.",
                new String[0],
                0.15
        );

        if (userConfirmedDecision) {
            String decision = "\n\n## " + stamp + " — решение подтверждено пользователем\n"
                    + "Рекомендация наставника сохранена как рабочее решение по явному подтверждению пользователя. "
                    + "Это не делает содержащиеся в ней внешние фактические утверждения проверенными.\n\n"
                    + limit(text, 1200) + "\n";
            append(file(context, DECISIONS), decision);
        }

        updateCheckpoint(context, "Ответ наставника сохранён; факты не повышались до VERIFIED", InitiativeResearchAgenda.latestResultText(context));
        appendEvent(context, "MENTOR_REPLY_IMPORTED", "Сохранён ответ наставника как предварительная заметка",
                "file=" + savedReply.getName() + "; indexed=" + added + "; userConfirmedDecision=" + userConfirmedDecision);
        return "Ответ сохранён локально как PRELIMINARY. В фактическую память он не добавлен; найдено предварительных записей для поиска: " + added + ".";
    }

    public static synchronized String latestReply(Context context) {
        ensureFiles(context);
        String value = read(file(context, LATEST_REPLY));
        return value.isEmpty() ? "Ответ наставника ещё не сохранялся." : value;
    }

    private static void updateCheckpoint(Context context, String status, String report) {
        String first = report == null ? "" : report.split("\\n", 2)[0].trim();
        String text = "# CHECKPOINT.md\n\n"
                + "Updated: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT).format(new Date()) + "\n\n"
                + "## Current goal\n"
                + "Создать проверяемый бесплатный цикл обучения Astra через ручной обмен с наставником без платного API.\n\n"
                + "## Current state\n- " + status + "\n"
                + "- Последний отчёт: " + (first.isEmpty() ? "пока нет" : first) + "\n"
                + "- Ответы наставника сохраняются в mentor-memory/replies и индексируются только как PRELIMINARY.\n"
                + "- Автоматическая запись в фактическую MemoryStore и VERIFIED_FACTS запрещена.\n\n"
                + "## Next step\n"
                + "Проверить источники, выбрать один конкретный эксперимент, записать результат и отдельно подтвердить любое решение.\n";
        write(file(context, CHECKPOINT), text);
    }

    private static void appendEvent(Context context, String type, String summary, String details) {
        try {
            JSONObject event = new JSONObject();
            event.put("timestamp", System.currentTimeMillis());
            event.put("type", type);
            event.put("summary", summary == null ? "" : summary);
            event.put("details", details == null ? "" : details);
            append(file(context, EVENTS), event.toString() + "\n");
        } catch (Exception ignored) { }
    }

    private static void writeIfMissing(File file, String content) {
        if (!file.exists()) write(file, content);
    }

    private static boolean write(File file, String content) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (FileOutputStream out = new FileOutputStream(file, false)) {
                out.write((content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
            }
            return true;
        } catch (Exception ignored) { return false; }
    }

    private static boolean append(File file, String content) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (FileOutputStream out = new FileOutputStream(file, true)) {
                out.write((content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
            }
            return true;
        } catch (Exception ignored) { return false; }
    }

    private static String read(File file) {
        if (file == null || !file.isFile()) return "";
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] bytes = new byte[(int)Math.min(file.length(), 500000)];
            int read = in.read(bytes);
            return read <= 0 ? "" : new String(bytes, 0, read, StandardCharsets.UTF_8);
        } catch (Exception ignored) { return ""; }
    }

    private static String readTail(File file, int maxChars) {
        String s = read(file);
        return s.length() <= maxChars ? s : "[... начало скрыто; показан конец файла ...]\n" + s.substring(s.length() - maxChars);
    }

    private static String limit(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) return value == null ? "" : value;
        return value.substring(0, maxChars) + "\n[... текст сокращён для передачи ...]";
    }
}
