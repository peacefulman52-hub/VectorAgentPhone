package com.vectoragent.phone;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Bridges extractive web reports into the local memory graph.
 * Findings remain provisional; distinct domains are not treated as proof.
 */
public final class ResearchMemoryBridge {
    public static final class Summary {
        public int findingsSeen;
        public int added;
        public int linked;
        public int attached;
        public int skipped;
        public int errors;

        public String toDisplayString() {
            return "Извлечено фрагментов: " + findingsSeen
                    + "; новых предварительных выводов: " + added
                    + "; связей с похожими знаниями: " + linked
                    + "; дополнительных свидетельств к существующим записям: " + attached
                    + "; пропущено/повторено: " + skipped
                    + (errors > 0 ? "; ошибок записи: " + errors : "");
        }
    }

    private static final class Finding {
        final String text;
        final String url;
        Finding(String text, String url) { this.text = text; this.url = url; }
    }

    private ResearchMemoryBridge() {}

    public static Summary ingest(MemoryStore store, String question, String report) {
        Summary summary = new Summary();
        if (store == null || report == null || report.trim().isEmpty()) return summary;

        List<Finding> findings = extractFindings(report);
        summary.findingsSeen = findings.size();
        for (Finding finding : findings) {
            try {
                String outcome = store.addResearchCandidate(finding.text, question, finding.url);
                if (outcome == null || outcome.startsWith("SKIPPED:") || outcome.startsWith("ALREADY_ATTACHED:")) {
                    summary.skipped++;
                } else if (outcome.startsWith("ATTACHED:")) {
                    summary.attached++;
                } else if (outcome.startsWith("ADDED:")) {
                    summary.added++;
                    int marker = outcome.lastIndexOf(":LINKS:");
                    if (marker >= 0) {
                        try { summary.linked += Integer.parseInt(outcome.substring(marker + 7)); }
                        catch (NumberFormatException ignored) {}
                    }
                } else {
                    summary.errors++;
                }
            } catch (Exception ignored) {
                summary.errors++;
            }
        }
        return summary;
    }

    private static List<Finding> extractFindings(String report) {
        List<Finding> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String[] lines = report.split("\\r?\\n");
        String currentUrl = "";
        int mode = 0; // 0=outside evidence, 1=page sentences, 2=search snippet
        for (String raw : lines) {
            String line = raw == null ? "" : raw.trim();
            if (line.startsWith("URL: ")) {
                currentUrl = line.substring(5).trim();
                mode = 0;
                continue;
            }
            if (line.equals("Фрагменты страницы:")) {
                mode = 1;
                continue;
            }
            if (line.startsWith("Фрагмент поисковой выдачи")) {
                mode = 2;
                continue;
            }
            if (line.isEmpty() || line.startsWith("Что пока НЕ установлено")
                    || line.startsWith("Следующий шаг:")) {
                mode = 0;
                continue;
            }
            if (mode == 0 || !isHttp(currentUrl)) continue;
            String content = line;
            if (content.startsWith("•")) content = content.substring(1).trim();
            if (mode == 1 && !raw.trim().startsWith("•")) continue;
            if (content.isEmpty()) continue;

            for (String piece : content.split("(?<=[.!?])\\s+")) {
                String sentence = piece.trim();
                if (sentence.length() < 60) continue;
                if (sentence.length() > 520) {
                    int cut = sentence.lastIndexOf(' ', 520);
                    if (cut >= 60) sentence = sentence.substring(0, cut).trim();
                    else continue;
                }
                if (!hasEnoughContentWords(sentence)) continue;
                String key = normalize(sentence) + "|" + currentUrl;
                if (seen.add(key)) {
                    out.add(new Finding(sentence, currentUrl));
                    if (out.size() >= 12) return out;
                }
            }
        }
        return out;
    }

    private static boolean hasEnoughContentWords(String text) {
        String normalized = normalize(text);
        String[] words = normalized.split("\\s+");
        int count = 0;
        for (String word : words) {
            if (word.length() < 4 || isStopWord(word)) continue;
            count++;
        }
        return count >= 4;
    }

    private static boolean isStopWord(String word) {
        return "это этот эта эти того также когда который которая которые между через после перед может могут нужно если для или его нее они она оно что как при над под без быть были был была this that these those with from into about when where which what who will would have has been their there then than your they them does using used also such some more most less only very each both and for are was were not but you can may its our all".contains(" " + word + " ");
    }

    private static String normalize(String text) {
        return (text == null ? "" : text).toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{Nd}]+", " ").replaceAll("\\s+", " ").trim();
    }

    private static boolean isHttp(String url) {
        return url != null && (url.startsWith("https://") || url.startsWith("http://"));
    }
}
