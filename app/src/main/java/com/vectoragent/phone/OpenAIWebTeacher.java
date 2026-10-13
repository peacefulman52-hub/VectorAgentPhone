package com.vectoragent.phone;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class OpenAIWebTeacher {
    public static final class Result {
        public String report;
        public String[] urls;
        Result(String r, String[] u) { report=r; urls=u; }
    }

    private OpenAIWebTeacher() {}

    /** Legacy tutor path: passes a bounded memory summary for comparison. */
    public static Result research(String apiKey, String claim, MemoryStore store) throws Exception {
        StringBuilder memory = new StringBuilder();
        for (MemoryStore.Item x : store.all()) {
            if ("REJECTED".equals(x.status)) continue;
            memory.append("- [").append(x.status).append("] ").append(x.text)
                    .append(" | confidence=").append(Math.round(x.confidence * 100))
                    .append("% | support=").append(x.support).append("\n");
            if (memory.length() > 9000) break;
        }
        return performResearch(apiKey, claim, memory.toString());
    }

    /**
     * Initiative path: sends only the selected research question. The device's
     * fact memory is deliberately not added to this request.
     */
    public static Result researchQuestion(String apiKey, String question) throws Exception {
        return performResearch(apiKey, question, "Локальная память устройства не передавалась в этот запрос.");
    }

    private static Result performResearch(String apiKey, String question, String memoryContext) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) throw new IOException("OpenAI API key не настроен");
        if (question == null || question.trim().isEmpty()) throw new IOException("Исследовательский вопрос пуст");

        String instructions =
                "Ты — исследовательский помощник для локального агента Astra. Отвечай по-русски. " +
                "Не переписывай память и не объявляй истину на основании собственного авторитета. " +
                "Исследуй вопрос с помощью веб-поиска. Ищи минимум 3 независимых источника; предпочитай первичные, официальные и научные публикации. " +
                "Укажи даты публикации, если доступны. Не считай две страницы одного издателя независимыми. " +
                "Структура: (1) ключевые выводы с явными цитатами-ссылками, (2) согласие и разногласия источников, " +
                "(3) ограничения и неопределённость, (4) практическое следующее действие или эксперимент для Astra. " +
                "Отделяй установленные факты от рекомендаций; не придумывай URL. " +
                "Контекст: " + memoryContext + "\n\nИсследовательский вопрос:\n" + question;

        JSONObject body = new JSONObject();
        body.put("model", "gpt-5.5");
        body.put("tools", new JSONArray().put(new JSONObject().put("type", "web_search")));
        body.put("tool_choice", "required");
        body.put("input", new JSONArray()
                .put(new JSONObject().put("role", "system").put("content",
                        "Проводи аккуратное исследование, цитируй ссылки и явно обозначай неопределённость."))
                .put(new JSONObject().put("role", "user").put("content", instructions)));

        HttpURLConnection c = (HttpURLConnection) new URL("https://api.openai.com/v1/responses").openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        c.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
        c.setRequestProperty("Content-Type", "application/json");
        c.setDoOutput(true);
        try (OutputStream out = c.getOutputStream()) {
            out.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }

        int code = c.getResponseCode();
        InputStream in = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        StringBuilder raw = new StringBuilder();
        if (in != null) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) raw.append(line);
            }
        }
        if (code < 200 || code >= 300) {
            String error = raw.toString();
            if (error.length() > 500) error = error.substring(0, 500) + "…";
            throw new IOException("HTTP " + code + ": " + error);
        }

        JSONObject root = new JSONObject(raw.toString());
        String report = root.optString("output_text", "");
        if (report.isEmpty()) report = extractText(root);
        if (report.trim().isEmpty()) throw new IOException("API не вернул текстовый отчёт");

        List<String> urls = new ArrayList<>();
        collectUrls(root, urls);
        return new Result(report, urls.toArray(new String[0]));
    }

    private static String extractText(JSONObject root) {
        StringBuilder s = new StringBuilder();
        JSONArray out = root.optJSONArray("output");
        if (out == null) return "";
        for (int i = 0; i < out.length(); i++) {
            JSONObject item = out.optJSONObject(i);
            if (item == null) continue;
            JSONArray content = item.optJSONArray("content");
            if (content == null) continue;
            for (int j = 0; j < content.length(); j++) {
                JSONObject part = content.optJSONObject(j);
                if (part != null && "output_text".equals(part.optString("type")))
                    s.append(part.optString("text", ""));
            }
        }
        return s.toString();
    }

    private static void collectUrls(JSONObject obj, List<String> urls) {
        if (obj == null) return;
        JSONArray names = obj.names();
        if (names == null) return;
        for (int i = 0; i < names.length(); i++) {
            String key = names.optString(i);
            Object value = obj.opt(key);
            if (value instanceof JSONObject) {
                collectUrls((JSONObject) value, urls);
            } else if (value instanceof JSONArray) {
                JSONArray array = (JSONArray) value;
                for (int j = 0; j < array.length(); j++) {
                    Object item = array.opt(j);
                    if (item instanceof JSONObject) collectUrls((JSONObject) item, urls);
                }
            } else if ("url".equals(key) && value instanceof String) {
                String u = (String) value;
                if ((u.startsWith("https://") || u.startsWith("http://")) && !urls.contains(u)) urls.add(u);
            }
        }
    }
}
