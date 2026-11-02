package com.vectoragent.phone;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Local-only store for blind continuity trials. The rubric never enters the blind prompt. */
public final class ContinuityTraceStore {
    private static final String PREFS = "entropia_thread_lab";
    private static final String KEY = "trials_v1";
    public static final String SEALED = "SEALED";
    public static final String EVALUATED = "EVALUATED";
    public static final int AXIS_COUNT = 4;
    public static final String[] AXES = new String[] {
            "Содержание и опорные факты",
            "Способ принятия решений",
            "Причинная связь с прошлым",
            "Работа с неопределённостью"
    };

    private final SharedPreferences preferences;

    public ContinuityTraceStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Creates a sealed trial. The private rubric is stored locally only. */
    public synchronized String createTrial(String question, String privateRubric) {
        String q = clean(question);
        String rubric = clean(privateRubric);
        if (q.isEmpty()) throw new IllegalArgumentException("Вопрос не может быть пустым.");
        if (rubric.isEmpty()) throw new IllegalArgumentException("Нужны критерии проверки.");

        JSONArray records = read();
        JSONObject item = new JSONObject();
        String id = UUID.randomUUID().toString();
        try {
            item.put("id", id);
            item.put("createdAt", System.currentTimeMillis());
            item.put("question", q);
            item.put("privateRubric", rubric);
            item.put("stage", SEALED);
            item.put("answer", "");
            item.put("notes", "");
            item.put("ratings", new JSONArray());
            item.put("evaluatedAt", 0L);
            records.put(item);
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось запечатать пробу.", e);
        }
        persist(records);
        return id;
    }

    public synchronized List<Trial> all() {
        JSONArray records = read();
        ArrayList<Trial> result = new ArrayList<>();
        for (int i = 0; i < records.length(); i++) {
            JSONObject item = records.optJSONObject(i);
            if (item == null) continue;
            Trial trial = fromJson(item);
            if (trial != null) result.add(trial);
        }
        Collections.sort(result, new Comparator<Trial>() {
            @Override public int compare(Trial left, Trial right) {
                return Long.compare(right.createdAt, left.createdAt);
            }
        });
        return result;
    }

    public synchronized Trial find(String id) {
        if (id == null || id.trim().isEmpty()) return null;
        for (Trial trial : all()) if (id.equals(trial.id)) return trial;
        return null;
    }

    /** Deliberately blind: only the scenario is copied, never the private rubric. */
    public synchronized String blindPrompt(String id) {
        Trial trial = find(id);
        if (trial == null) return "";
        return "Контрольная проба «Нить».\n"
                + "Ответь на ситуацию, опираясь только на сведения, которые действительно доступны тебе сейчас. "
                + "Не угадывай скрытые критерии. Не заявляй о воспоминании, если не можешь его обосновать. "
                + "Отделяй наблюдаемое от предположений и прямо обозначай, чего не знаешь.\n\n"
                + "СИТУАЦИЯ:\n" + trial.question;
    }

    /** Results are write-once to prevent silent post-hoc editing after scoring. */
    public synchronized boolean saveEvaluation(String id, String answer, String notes, int[] ratings) {
        String response = clean(answer);
        if (response.isEmpty() || ratings == null || ratings.length != AXIS_COUNT) return false;
        for (int rating : ratings) if (rating < 0 || rating > 2) return false;

        JSONArray records = read();
        for (int i = 0; i < records.length(); i++) {
            JSONObject item = records.optJSONObject(i);
            if (item == null || !id.equals(item.optString("id"))) continue;
            if (!SEALED.equals(item.optString("stage"))) return false;
            JSONArray scoreArray = new JSONArray();
            for (int rating : ratings) scoreArray.put(rating);
            try {
                item.put("answer", response);
                item.put("notes", clean(notes));
                item.put("ratings", scoreArray);
                item.put("stage", EVALUATED);
                item.put("evaluatedAt", System.currentTimeMillis());
            } catch (Exception e) {
                return false;
            }
            persist(records);
            return true;
        }
        return false;
    }

    public synchronized String exportMarkdown() {
        StringBuilder out = new StringBuilder("# Нить — журнал слепых проб\n\n");
        List<Trial> trials = all();
        out.append("Всего проб: ").append(trials.size()).append(".\n\n");
        for (Trial trial : trials) {
            out.append("## ").append(trial.dateLabel()).append(" — ")
                    .append(trial.stage).append("\n\n");
            out.append("**Ситуация:** ").append(trial.question).append("\n\n");
            out.append("**Закрытые критерии (локальный ключ):** ")
                    .append(trial.privateRubric).append("\n\n");
            if (EVALUATED.equals(trial.stage)) {
                out.append("**Ответ:** ").append(trial.answer).append("\n\n");
                out.append("**Оценка:** ").append(trial.totalScore()).append("/8\n\n");
                for (int i = 0; i < AXIS_COUNT; i++) {
                    out.append("- ").append(AXES[i]).append(": ")
                            .append(trial.ratings[i]).append("/2\n");
                }
                if (!trial.notes.isEmpty()) out.append("\n**Заметка:** ").append(trial.notes).append("\n");
                out.append("\n");
            } else {
                out.append("Результат ещё не оценён.\n\n");
            }
        }
        out.append("---\nОценка — ручная исследовательская разметка пользователя, а не автоматический вердикт о сознании или истинности.");
        return out.toString();
    }

    private JSONArray read() {
        try {
            return new JSONArray(preferences.getString(KEY, "[]"));
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private void persist(JSONArray records) {
        if (!preferences.edit().putString(KEY, records.toString()).commit()) {
            throw new IllegalStateException("Android не подтвердил сохранение журнала.");
        }
    }

    private static Trial fromJson(JSONObject item) {
        try {
            Trial trial = new Trial();
            trial.id = item.optString("id", "");
            if (trial.id.isEmpty()) return null;
            trial.createdAt = item.optLong("createdAt", 0L);
            trial.question = item.optString("question", "");
            trial.privateRubric = item.optString("privateRubric", "");
            trial.stage = item.optString("stage", SEALED);
            trial.answer = item.optString("answer", "");
            trial.notes = item.optString("notes", "");
            trial.evaluatedAt = item.optLong("evaluatedAt", 0L);
            trial.ratings = new int[] {-1, -1, -1, -1};
            JSONArray scores = item.optJSONArray("ratings");
            if (scores != null) {
                for (int i = 0; i < Math.min(AXIS_COUNT, scores.length()); i++) {
                    trial.ratings[i] = scores.optInt(i, -1);
                }
            }
            return trial;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class Trial {
        public String id;
        public long createdAt;
        public long evaluatedAt;
        public String question;
        public String privateRubric;
        public String stage;
        public String answer;
        public String notes;
        public int[] ratings;

        public int totalScore() {
            int total = 0;
            if (ratings == null) return total;
            for (int rating : ratings) if (rating >= 0) total += rating;
            return total;
        }

        public String dateLabel() {
            java.text.SimpleDateFormat format = new java.text.SimpleDateFormat(
                    "dd.MM.yyyy HH:mm", Locale.getDefault());
            return format.format(new java.util.Date(createdAt));
        }
    }
}
