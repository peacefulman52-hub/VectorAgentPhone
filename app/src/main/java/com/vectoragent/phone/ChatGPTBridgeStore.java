package com.vectoragent.phone;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Explicitly enabled, local-only capture of visible ChatGPT conversation text.
 * Captured text is preliminary and is never promoted to factual MemoryStore knowledge.
 */
public final class ChatGPTBridgeStore {
    private static final String PREFS = "entropia_chatgpt_bridge";
    private static final String MONITOR = "monitoring_enabled";
    private static final String SNAPSHOT = "latest_snapshot";
    private static final String PREVIOUS_LINES = "previous_lines";
    private static final String ARMED = "capture_armed";
    private static final String REPLY = "latest_reply";
    private static final String IMPORTED_REPLY = "latest_imported_reply";
    private static final String IMPORTED_DIGEST = "imported_digest";
    private static final String LAST_ARCHIVED = "last_archived_snapshot";
    private static final String TRIGGER = "пиши энтропии";

    private ChatGPTBridgeStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void setMonitoringEnabled(Context c, boolean enabled) {
        prefs(c).edit().putBoolean(MONITOR, enabled).apply();
    }

    public static boolean isMonitoringEnabled(Context c) {
        return prefs(c).getBoolean(MONITOR, false);
    }

    public static boolean isChatGPTPackage(String packageName) {
        return "com.openai.chatgpt".equals(packageName);
    }

    public static boolean isChatGPTBrowserPage(List<String> texts) {
        if (texts == null) return false;
        for (String text : texts) {
            String s = text == null ? "" : text.toLowerCase(Locale.ROOT);
            if (s.contains("chatgpt.com") || s.contains("chat.openai.com")) return true;
        }
        return false;
    }

    public static synchronized void observeVisibleTexts(Context c, String packageName, List<String> visibleTexts) {
        if (!isMonitoringEnabled(c) || visibleTexts == null) return;
        ArrayList<String> lines = clean(visibleTexts);
        if (lines.isEmpty()) return;
        SharedPreferences p = prefs(c);
        ArrayList<String> previous = decode(p.getString(PREVIOUS_LINES, "[]"));
        String previousSnapshot = p.getString(SNAPSHOT, "");
        String snapshot = join(lines, "\n");
        int triggerCount = countTriggers(lines);
        int oldTriggerCount = countTriggers(previous);
        int triggerIndex = lastTrigger(lines);
        boolean newTrigger = triggerCount > oldTriggerCount;
        if (!newTrigger && triggerIndex >= 0 && triggerIndex >= previous.size()
                && !snapshot.equals(p.getString(SNAPSHOT, ""))) newTrigger = true;

        SharedPreferences.Editor edit = p.edit();
        if (newTrigger && triggerIndex >= 0) {
            edit.putBoolean(ARMED, true).putString(IMPORTED_DIGEST, "").putString(REPLY, "");
        }
        boolean armed = newTrigger || p.getBoolean(ARMED, false);
        if (armed && triggerIndex >= 0) {
            ArrayList<String> answer = new ArrayList<>();
            for (int i = triggerIndex + 1; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (!line.isEmpty() && !isUiLabel(line)) answer.add(line);
            }
            String reply = join(answer, "\n").trim();
            if (reply.length() >= 20 && !reply.equals(p.getString(REPLY, ""))) {
                edit.putString(REPLY, reply);
                writeText(new File(storage(c), "LATEST_GPT_REPLY.md"),
                        "# Ответ GPT для Энтропии\n\nСтатус: PRELIMINARY; не проверенный факт.\n\n" + reply + "\n");
            }
        }
        edit.putString(SNAPSHOT, snapshot)
                .putString(PREVIOUS_LINES, new JSONArray(lines).toString())
                .putString("latest_package", packageName == null ? "" : packageName)
                .putLong("last_observed_at", System.currentTimeMillis()).apply();
        if (!snapshot.equals(previousSnapshot)) {
            writeText(new File(storage(c), "LATEST_CHATGPT_SCREEN.md"),
                    "# Видимый текст ChatGPT\n\nСтатус: PRELIMINARY; локальный фрагмент интерфейса.\n\n" + snapshot + "\n");
        }
    }

    public static synchronized boolean hasPendingReply(Context c) {
        SharedPreferences p = prefs(c);
        return p.getBoolean(ARMED, false) && p.getString(REPLY, "").trim().length() >= 20;
    }

    /** Save a codeword reply once the visible answer has stopped changing. */
    public static synchronized void finalizeCapturedReply(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.getBoolean(ARMED, false)) return;
        String reply = p.getString(REPLY, "").trim();
        if (reply.length() < 20) return;
        String digest = reply.length() + ":" + reply.hashCode();
        if (!digest.equals(p.getString(IMPORTED_DIGEST, ""))) {
            String result = MentorExchangeStore.importMentorReply(c, reply, false);
            if (!result.startsWith("Обработка завершена")) return;
            p.edit().putString(IMPORTED_DIGEST, digest).putString(IMPORTED_REPLY, reply)
                    .putBoolean(ARMED, false).apply();
        } else {
            p.edit().putBoolean(ARMED, false).apply();
        }
        archiveCurrentScreen(c);
    }

    public static synchronized void finishSession(Context c) {
        archiveCurrentScreen(c);
        finalizeCapturedReply(c);
    }

    public static String getLatestSnapshot(Context c) { return prefs(c).getString(SNAPSHOT, ""); }
    public static String getLatestReply(Context c) { return prefs(c).getString(REPLY, ""); }
    public static String getLatestImportedReply(Context c) { return prefs(c).getString(IMPORTED_REPLY, ""); }

    public static synchronized int archiveCount(Context c) {
        File dir = new File(storage(c), "screens");
        File[] files = dir.listFiles((d, name) -> name.startsWith("chatgpt-screen-") && name.endsWith(".md"));
        return files == null ? 0 : files.length;
    }

    public static synchronized String historyDirectory(Context c) {
        return new File(storage(c), "screens").getAbsolutePath();
    }

    public static synchronized void archiveCurrentScreen(Context c) {
        SharedPreferences p = prefs(c);
        String snapshot = p.getString(SNAPSHOT, "").trim();
        if (snapshot.isEmpty() || snapshot.equals(p.getString(LAST_ARCHIVED, ""))) return;
        File dir = new File(storage(c), "screens");
        if (!dir.exists() && !dir.mkdirs()) return;
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ROOT).format(new Date());
        File target = new File(dir, "chatgpt-screen-" + stamp + ".md");
        int suffix = 1;
        while (target.exists()) target = new File(dir, "chatgpt-screen-" + stamp + "-" + suffix++ + ".md");
        String content = "# Видимый фрагмент диалога ChatGPT — " + stamp + "\n\n"
                + "Статус: PRELIMINARY; локальный экранный фрагмент, не доказательство.\n"
                + "Пакет: " + p.getString("latest_package", "unknown") + "\n\n" + snapshot + "\n";
        if (writeText(target, content)) {
            p.edit().putString(LAST_ARCHIVED, snapshot).apply();
            writeText(new File(storage(c), "LATEST_CHATGPT_SCREEN.md"), content);
        }
    }

    private static ArrayList<String> clean(List<String> texts) {
        ArrayList<String> result = new ArrayList<>();
        for (String raw : texts) {
            if (raw == null) continue;
            for (String part : raw.replace("\r", "\n").split("\n")) {
                String s = part.trim();
                if (s.length() < 2 || isUiLabel(s)) continue;
                if (result.isEmpty() || !result.get(result.size() - 1).equals(s)) result.add(s);
            }
        }
        return result;
    }

    private static boolean isUiLabel(String raw) {
        String s = norm(raw);
        String[] labels = { "chatgpt", "new chat", "новый чат", "search", "поиск", "share", "поделиться",
                "copy", "копировать", "read aloud", "читать вслух", "regenerate", "повторить ответ",
                "send", "отправить", "message chatgpt", "сообщение chatgpt", "more", "ещё", "menu", "меню",
                "log in", "sign in", "войти", "temporary chat", "временный чат", "stop generating",
                "chatgpt can make mistakes", "chatgpt может допускать ошибки" };
        for (String label : labels) if (s.equals(label)) return true;
        return false;
    }

    private static int countTriggers(List<String> lines) {
        int n = 0;
        for (String line : lines) if (norm(line).contains(TRIGGER)) n++;
        return n;
    }

    private static int lastTrigger(List<String> lines) {
        for (int i = lines.size() - 1; i >= 0; i--) if (norm(lines.get(i)).contains(TRIGGER)) return i;
        return -1;
    }

    private static String norm(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{Nd}]+", " ").trim();
    }

    private static ArrayList<String> decode(String raw) {
        ArrayList<String> result = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(raw == null ? "[]" : raw);
            for (int i = 0; i < a.length(); i++) {
                String s = a.optString(i, "").trim();
                if (!s.isEmpty()) result.add(s);
            }
        } catch (Exception ignored) {}
        return result;
    }

    private static String join(List<String> lines, String separator) {
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            if (line == null || line.trim().isEmpty()) continue;
            if (out.length() > 0) out.append(separator);
            out.append(line.trim());
        }
        return out.toString();
    }

    private static File storage(Context c) { return new File(c.getFilesDir(), "chatgpt-bridge"); }

    private static boolean writeText(File file, String value) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) return false;
            try (FileOutputStream out = new FileOutputStream(file, false)) {
                out.write((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
            return true;
        } catch (Exception ignored) { return false; }
    }
}
