package com.vectoragent.phone;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic, offline structure extraction for manually pasted mentor replies.
 * This is lexical triage, not an LLM or a truth verifier. Unclassified text is reported
 * as such; extracted material remains preliminary.
 */
public final class MentorReplyAnalyzer {
    private static final Pattern MARKER = Pattern.compile(
            "(?i)\\b(?:MENTOR-[A-Z0-9-]+|E-\\d{2}|PRELIMINARY|VERIFIED_FACTS)\\b");
    private static final Pattern URL = Pattern.compile("https?://[^\\s)]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern SENTENCE = Pattern.compile("(?<=[.!?])\\s+|\\n+");
    private MentorReplyAnalyzer() {}

    public static final class Analysis {
        public final List<String> markers = new ArrayList<>();
        public final List<String> claims = new ArrayList<>();
        public final List<String> recommendations = new ArrayList<>();
        public final List<String> doubts = new ArrayList<>();
        public final List<String> experiments = new ArrayList<>();
        public final List<String> urls = new ArrayList<>();
        public final List<String> unclassified = new ArrayList<>();

        public String toMarkdown() {
            StringBuilder out = new StringBuilder();
            out.append("# Отчёт локальной обработки ответа наставника\n\n")
               .append("Тип обработки: локальный структурный разбор по словам-маркерам; ")
               .append("это НЕ семантическое понимание и НЕ проверка истинности.\n\n")
               .append("Статус всех извлечённых пунктов: PRELIMINARY / НЕ ПРОВЕРЕНО.\n\n")
               .append("## Контрольные маркеры (").append(markers.size()).append(")\n");
            appendList(out, markers);
            out.append("\n## Утверждения/наблюдения (").append(claims.size()).append(")\n");
            appendList(out, claims);
            out.append("\n## Рекомендации (").append(recommendations.size()).append(")\n");
            appendList(out, recommendations);
            out.append("\n## Сомнения/ограничения (").append(doubts.size()).append(")\n");
            appendList(out, doubts);
            out.append("\n## Эксперименты/проверки (").append(experiments.size()).append(")\n");
            appendList(out, experiments);
            out.append("\n## URL (").append(urls.size()).append(")\n");
            appendList(out, urls);
            out.append("\n## Не удалось классифицировать автоматически (").append(unclassified.size()).append(")\n");
            appendList(out, unclassified);
            out.append("\n## Защитные ограничения\n")
               .append("- Оригинал ответа хранится отдельно от этого отчёта.\n")
               .append("- Ни один пункт не становится VERIFIED_FACTS автоматически.\n")
               .append("- Классификация по ключевым словам может ошибаться; проверь пункты вручную.\n");
            return out.toString();
        }
    }

    public static Analysis analyze(String raw) {
        Analysis a = new Analysis();
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) return a;

        Matcher markerMatcher = MARKER.matcher(text);
        while (markerMatcher.find()) addUnique(a.markers, markerMatcher.group());
        Matcher urlMatcher = URL.matcher(text);
        while (urlMatcher.find()) addUnique(a.urls, trimUrl(urlMatcher.group()));

        String[] lines = text.split("\\r?\\n");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;
            line = line.replaceFirst("^[#>*•\\-]+\\s*", "").trim();
            if (line.isEmpty()) continue;
            String lower = line.toLowerCase(Locale.ROOT);
            if (isHeading(lower)) continue;
            if (containsAny(lower, "эксперимент", "проверь", "проверить", "тест", "ожидаемый результат",
                    "критерий успех", "перезапуск", "контрольная фраза", "experiment", "test", "verify")) {
                addUnique(a.experiments, line);
            } else if (containsAny(lower, "не доказ", "сомнен", "не провер", "не утверждай", "не является",
                    "ограничен", "возможно", "противореч", "недостаточно", "не считай", "uncertain",
                    "not proven", "not verified", "limitation", "contradiction")) {
                addUnique(a.doubts, line);
            } else if (containsAny(lower, "рекоменд", "нужно", "следует", "предлага", "не добавляй",
                    "сохрани", "записывай", "не записывай", "не изменяй", "не меняй", "действие:",
                    "recommend", "should", "do not", "don't", "save", "avoid")) {
                addUnique(a.recommendations, line);
            } else if (containsAny(lower, "статус:", "источник:", "контрольная фраза:", "ожидаемое действие:",
                    "важно:", "это диагностическое сообщение")) {
                addUnique(a.claims, line);
            } else if (line.length() >= 35) {
                addUnique(a.unclassified, line);
            }
        }
        return a;
    }

    private static boolean isHeading(String s) {
        return s.startsWith("ответ наставника") || s.startsWith("проверка канала")
                || s.startsWith("проверь данные") || s.startsWith("задача:")
                || s.startsWith("верни ответ") || s.startsWith("=== ");
    }
    private static boolean containsAny(String s, String... words) {
        for (String word : words) if (s.contains(word)) return true;
        return false;
    }
    private static void addUnique(List<String> list, String value) {
        if (value != null && !value.trim().isEmpty() && !list.contains(value.trim())) list.add(value.trim());
    }
    private static String trimUrl(String url) {
        return url.replaceAll("[.,;!?]+$", "");
    }
    private static void appendList(StringBuilder out, List<String> values) {
        if (values.isEmpty()) { out.append("- Ничего не выделено.\n"); return; }
        for (String value : values) out.append("- ").append(value.replace("\n", " ")).append("\n");
    }
}
