package com.vectoragent.phone;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Persistent store for preliminary findings extracted from free web research.
 * Findings remain separate observations; clustering links related wording without
 * silently merging claims or promoting a report to factual MemoryStore.
 */
public final class ResearchKnowledgeStore {
    private static final String PREFS = "vector_research_knowledge";
    private static final String OBSERVATIONS = "observations";
    private static final String CLUSTERS = "clusters";
    private static final int MAX_OBSERVATIONS = 600;
    private static final int MAX_CLUSTERS = 250;
    private static final int MAX_TEXT = 1000;

    private ResearchKnowledgeStore() {}

    private static final class Finding {
        String text, url, origin, basis;
        double confidence;
        List<String> evidenceUrls;

        Finding(String text, String url) {
            this(text, url == null || url.trim().isEmpty()
                            ? Collections.<String>emptyList()
                            : Collections.singletonList(url),
                    "FREE_WEB_EXTRACT", 0.25, "");
        }

        Finding(String text, List<String> evidenceUrls, String origin, double confidence, String basis) {
            this.text = text == null ? "" : text;
            this.evidenceUrls = new ArrayList<>();
            if (evidenceUrls != null) for (String raw : evidenceUrls) {
                String u = safeUrl(raw);
                if (!u.isEmpty() && !this.evidenceUrls.contains(u)) this.evidenceUrls.add(u);
            }
            this.url = this.evidenceUrls.isEmpty() ? "" : this.evidenceUrls.get(0);
            this.origin = origin == null ? "FREE_WEB_EXTRACT" : origin;
            this.confidence = Math.max(0.05, Math.min(0.45, confidence));
            this.basis = basis == null ? "" : basis.trim();
        }
    }

    /**
     * Stores useful excerpts from a report and attaches each excerpt to its closest
     * topical cluster. Returns the number of usable findings extracted; repeated
     * excerpts from the same source still count as evidence, while empty reports return zero.
     */
    public static synchronized int ingestReport(Context context, String title, String question,
                                                String report, String[] urls) {
        if (context == null || report == null || report.trim().isEmpty()) return 0;
        return ingestFindings(context, title, question, extractFindings(report, urls));
    }

    /**
     * Adds an agent-authored tentative conclusion to the same cluster graph as extracted
     * evidence. The statement remains PRELIMINARY, with provenance and low confidence;
     * it is never promoted to factual MemoryStore.
     */
    public static synchronized int addPreliminaryConclusion(Context context, String conclusion,
            String title, String question, String basis, String[] evidenceUrls, double confidence) {
        if (context == null || conclusion == null || conclusion.trim().length() < 20) return 0;
        List<String> evidence = new ArrayList<>();
        if (evidenceUrls != null) {
            for (String raw : evidenceUrls) {
                String url = safeUrl(raw);
                if (!url.isEmpty() && !evidence.contains(url)) evidence.add(url);
            }
        }
        double cautiousConfidence = Math.max(0.05, Math.min(0.45, confidence));
        List<Finding> findings = new ArrayList<>();
        findings.add(new Finding(conclusion.trim(), evidence, "AGENT_PRELIMINARY_CONCLUSION",
                cautiousConfidence, basis == null ? "" : basis.trim()));
        return ingestFindings(context, title, question, findings);
    }

    private static int ingestFindings(Context context, String title, String question,
                                      List<Finding> findings) {
        if (context == null || findings == null || findings.isEmpty()) return 0;
        Context app = context.getApplicationContext();
        SharedPreferences p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONArray observations = readArray(p.getString(OBSERVATIONS, "[]"));
        JSONArray clusters = readArray(p.getString(CLUSTERS, "[]"));
        int added = 0;
        int usableFindings = 0;
        long now = System.currentTimeMillis();
        try {
        for (Finding f : findings) {
            String text = shorten(f.text, MAX_TEXT);
            int minLength = "AGENT_PRELIMINARY_CONCLUSION".equals(f.origin) ? 20 : 35;
            if (text.length() < minLength) continue;
            String url = safeUrl(f.url);
            if ("FREE_WEB_EXTRACT".equals(f.origin) && url.isEmpty()) continue;
            usableFindings++;
            String key = normalize(text);
            boolean duplicate = false;
            for (int i = 0; i < observations.length(); i++) {
                JSONObject old = observations.optJSONObject(i);
                if (old == null) continue;
                if (key.equals(normalize(old.optString("text")))
                        && url.equals(old.optString("sourceUrl"))
                        && f.origin.equals(old.optString("origin", "FREE_WEB_EXTRACT"))) {
                    old.put("repeatCount", old.optInt("repeatCount", 1) + 1);
                    old.put("lastSeenAt", now);
                    JSONArray priorEvidence = old.optJSONArray("evidenceUrls");
                    if (priorEvidence == null) priorEvidence = new JSONArray();
                    for (String evidenceUrl : f.evidenceUrls) {
                        if (!safeUrl(evidenceUrl).isEmpty() && !containsString(priorEvidence, safeUrl(evidenceUrl))) {
                            priorEvidence.put(safeUrl(evidenceUrl));
                        }
                    }
                    old.put("evidenceUrls", priorEvidence);
                    if (!f.basis.isEmpty()) old.put("basis", f.basis);
                    old.put("confidence", Math.min(0.45,
                            Math.max(old.optDouble("confidence", 0.25), f.confidence)));
                    duplicate = true;
                    break;
                }
            }
            if (duplicate) continue;

            ClusterMatch match = findCluster(observations, clusters, text);
            String clusterId;
            String relation;
            double similarity;
            if (match == null) {
                clusterId = "rk-" + Long.toHexString(now) + "-" + observations.length();
                relation = "NEW_CLUSTER";
                similarity = 0.0;
                JSONObject cluster = new JSONObject();
                cluster.put("id", clusterId);
                cluster.put("label", shorten(firstSentence(text), 90));
                cluster.put("members", new JSONArray());
                cluster.put("createdAt", now);
                cluster.put("updatedAt", now);
                clusters.put(cluster);
            } else {
                clusterId = match.clusterId;
                similarity = match.score;
                relation = classifyRelation(text, match.referenceText);
            }

            String referenceId = match == null ? "" : match.referenceId;
            String preliminaryConclusion;
            double conclusionConfidence;
            if ("POSSIBLE_CONTRADICTION_OR_CONDITION".equals(relation)) {
                preliminaryConclusion = "Возможно противоречие или различие условий с наблюдением "
                        + referenceId + ". Нужно сверить даты, определения и контекст; это не автоматическое опровержение.";
                conclusionConfidence = 0.20;
            } else if (match != null) {
                preliminaryConclusion = "Найдено тематически похожее, но отдельное наблюдение "
                        + referenceId + ". Общая закономерность пока не подтверждена.";
                conclusionConfidence = 0.25;
            } else {
                preliminaryConclusion = "Пока отдельное предварительное наблюдение: похожая исходная запись не найдена.";
                conclusionConfidence = 0.15;
            }

            String id = "obs-" + Long.toHexString(now) + "-" + observations.length();
            JSONObject observation = new JSONObject();
            observation.put("id", id);
            observation.put("text", text);
            observation.put("clusterId", clusterId);
            observation.put("relationToCluster", relation);
            observation.put("similarityToCluster", similarity);
            observation.put("similarToObservationId", referenceId);
            observation.put("reportTitle", title == null ? "" : title);
            observation.put("researchQuestion", question == null ? "" : question);
            observation.put("sourceUrl", url);
            observation.put("sourceDomain", domain(url));
            observation.put("evidenceUrls", toJsonArray(f.evidenceUrls));
            observation.put("origin", f.origin);
            observation.put("basis", f.basis);
            observation.put("status", "PRELIMINARY");
            observation.put("confidence", f.confidence);
            observation.put("preliminaryConclusion", preliminaryConclusion);
            observation.put("conclusionStatus", "CANDIDATE");
            observation.put("conclusionConfidence", conclusionConfidence);
            observation.put("conclusionBasis", "Локальная эвристика текстового сходства, отрицаний и чисел; не оценка истинности.");
            observation.put("basisObservationId", referenceId);
            observation.put("repeatCount", 1);
            observation.put("observedAt", now);
            observation.put("lastSeenAt", now);
            observations.put(observation);

            JSONObject cluster = findClusterById(clusters, clusterId);
            if (cluster != null) {
                JSONArray members = cluster.optJSONArray("members");
                if (members == null) members = new JSONArray();
                members.put(id);
                cluster.put("members", members);
                cluster.put("updatedAt", now);
            }
            added++;
        }

        // Bound local storage and discard orphaned cluster member references.
        while (observations.length() > MAX_OBSERVATIONS) observations.remove(0);
        Set<String> alive = new HashSet<>();
        for (int i = 0; i < observations.length(); i++) {
            JSONObject o = observations.optJSONObject(i);
            if (o != null) alive.add(o.optString("id"));
        }
        for (int i = clusters.length() - 1; i >= 0; i--) {
            JSONObject c = clusters.optJSONObject(i);
            if (c == null) { clusters.remove(i); continue; }
            JSONArray oldMembers = c.optJSONArray("members");
            JSONArray members = new JSONArray();
            if (oldMembers != null) for (int j = 0; j < oldMembers.length(); j++) {
                String member = oldMembers.optString(j);
                if (alive.contains(member)) members.put(member);
            }
            c.put("members", members);
            if (members.length() == 0) clusters.remove(i);
        }
        while (clusters.length() > MAX_CLUSTERS) clusters.remove(0);
        p.edit().putString(OBSERVATIONS, observations.toString())
                .putString(CLUSTERS, clusters.toString()).apply();
        return usableFindings;
        } catch (Exception ignored) {
            // Preserve any earlier valid state; malformed evidence must not crash the research loop.
            return 0;
        }
    }

    private static final class ClusterMatch {
        String clusterId, referenceText, referenceId;
        double score;
        ClusterMatch(String id, String text, String referenceId, double score) {
            clusterId = id; referenceText = text; this.referenceId = referenceId; this.score = score;
        }
    }

    private static ClusterMatch findCluster(JSONArray observations, JSONArray clusters, String text) {
        ClusterMatch best = null;
        for (int i = 0; i < clusters.length(); i++) {
            JSONObject cluster = clusters.optJSONObject(i);
            if (cluster == null) continue;
            JSONArray members = cluster.optJSONArray("members");
            if (members == null) continue;
            for (int j = Math.max(0, members.length() - 25); j < members.length(); j++) {
                String id = members.optString(j);
                for (int k = observations.length() - 1; k >= 0; k--) {
                    JSONObject old = observations.optJSONObject(k);
                    if (old == null || !id.equals(old.optString("id"))) continue;
                    // Derived conclusions may be cluster members, but must never
                    // become supporting evidence for another finding or conclusion.
                    if (!"FREE_WEB_EXTRACT".equals(old.optString("origin", "FREE_WEB_EXTRACT"))) break;
                    String previousText = old.optString("text");
                    double score = similarity(text, previousText);
                    if (score >= 0.27 && (best == null || score > best.score)) {
                        best = new ClusterMatch(cluster.optString("id"), previousText, id, score);
                    }
                    break;
                }
            }
        }
        return best;
    }

    private static JSONObject findClusterById(JSONArray clusters, String id) {
        for (int i = 0; i < clusters.length(); i++) {
            JSONObject c = clusters.optJSONObject(i);
            if (c != null && id.equals(c.optString("id"))) return c;
        }
        return null;
    }

    /** Lightweight, offline similarity: content-word overlap, tolerant of common inflections. */
    private static double similarity(String a, String b) {
        Set<String> x = terms(a), y = terms(b);
        if (x.isEmpty() || y.isEmpty()) return 0.0;
        int common = 0;
        for (String t : x) if (y.contains(t)) common++;
        if (common < 2) return 0.0; // A single shared generic word is not enough for a cluster.
        double jaccard = (double) common / (x.size() + y.size() - common);
        double containment = (double) common / Math.min(x.size(), y.size());
        return 0.55 * jaccard + 0.45 * containment;
    }

    private static Set<String> terms(String text) {
        String n = normalize(text);
        Set<String> stop = new HashSet<>(Arrays.asList(
                "этот","эта","эти","такой","такие","который","которая","которые","также","очень",
                "может","могут","нужно","когда","тогда","чтобы","после","перед","между","через",
                "будет","были","было","есть","это","для","или","как","при","что","его","нее","них",
                "the","and","with","from","into","this","that","these","those","when","where","what",
                "how","why","for","are","was","were","has","have","been","not","but","can","may"));
        Set<String> out = new LinkedHashSet<>();
        for (String word : n.split("\\s+")) {
            if (word.length() < 4 || stop.contains(word)) continue;
            out.add(stem(word));
        }
        return out;
    }

    private static String stem(String word) {
        String[] suffixes = {
            "иями","ями","ами","ого","его","ому","ему","ыми","ими","иях","ах","ях",
            "ую","юю","ая","яя","ое","ее","ые","ие","ый","ий","ой","ом","ем","ам",
            "ям","ов","ев","ей","ы","и","а","я","у","ю","е","о","ь",
            "ingly","edly","ation","ments","ment","ness","ing","ers","ies","ed","es","s"
        };
        for (String suffix : suffixes) {
            if (word.length() - suffix.length() >= 4 && word.endsWith(suffix))
                return word.substring(0, word.length() - suffix.length());
        }
        return word;
    }

    private static String classifyRelation(String newer, String older) {
        String a = normalize(newer), b = normalize(older);
        boolean negA = hasNegation(a), negB = hasNegation(b);
        List<Double> numsA = numbers(a), numsB = numbers(b);
        if (negA != negB || (!numsA.isEmpty() && !numsB.isEmpty() && !numsA.equals(numsB)))
            return "POSSIBLE_CONTRADICTION_OR_CONDITION";
        return "RELATED_UNVERIFIED";
    }

    private static boolean hasNegation(String text) {
        return Pattern.compile("(^|\\s)(не|нет|ни|без|никогда|not|no|without)(\\s|$)").matcher(text).find();
    }

    private static List<Double> numbers(String text) {
        List<Double> out = new ArrayList<>();
        Matcher m = Pattern.compile("(?<![\\p{L}])[-+]?\\d+(?:[.,]\\d+)?").matcher(text);
        while (m.find()) try {
            out.add(Double.parseDouble(m.group().replace(',', '.')));
        } catch (Exception ignored) {}
        return out;
    }

    /**
     * Drafts a deliberately modest extractive takeaway. It describes what was observed,
     * not whether the topic's substantive claim is true. It is stored as LOCAL_INFERENCE
     * and must never count as independent source evidence.
     */
    public static synchronized String draftConservativeConclusion(
            String title, String question, String report, String[] urls) {
        List<Finding> findings = extractFindings(report, urls);
        if (findings.isEmpty()) return "";
        Finding first = findings.get(0);
        if (first == null || first.text == null || first.text.trim().length() < 35 || safeUrl(first.url).isEmpty()) {
            return "";
        }
        Set<String> sourceDomains = new LinkedHashSet<>();
        for (Finding finding : findings) {
            String d = domain(finding.url);
            if (!d.isEmpty()) sourceDomains.add(d);
        }
        String topic = title == null ? "" : title.trim();
        if (topic.isEmpty()) topic = question == null ? "исследованной теме" : shorten(question.trim(), 90);
        topic = shorten(topic, 90);
        return "Предварительная сводка по теме «" + topic + "»: найден фрагмент «"
                + shorten(first.text, 210) + "». Извлечено фрагментов с источниками: "
                + findings.size() + "; доменов: " + sourceDomains.size()
                + ". Это не подтверждённый факт и не доказательство независимости источников; "
                + "следующий шаг — сверить первоисточник, дату, определения и условия.";
    }

    private static List<Finding> extractFindings(String report, String[] urls) {
        List<Finding> out = new ArrayList<>();
        String currentUrl = "";
        String fallbackUrl = singleUnambiguousUrl(urls);
        boolean capture = false;
        String[] lines = report.split("\\r?\\n");
        for (String raw : lines) {
            String line = raw.trim();
            if (line.matches("^\\d+\\.\\s+.*")) {
                capture = false;
                currentUrl = ""; // Never reuse the previous page's URL for the next source block.
            }
            if (line.startsWith("URL:")) {
                currentUrl = safeUrl(line.substring(4).trim());
                continue;
            }
            if (line.startsWith("Фрагменты страницы:") ||
                    line.startsWith("Фрагмент поисковой выдачи")) {
                capture = true;
                continue;
            }
            if (line.startsWith("Что пока НЕ установлено") ||
                    line.startsWith("Следующий шаг:") ||
                    line.startsWith("Метод:") || line.startsWith("Вопрос:")) {
                capture = false;
                continue;
            }
            if (!capture || line.isEmpty()) continue;
            if (line.startsWith("•")) line = line.substring(1).trim();
            String attributedUrl = currentUrl.isEmpty() ? fallbackUrl : currentUrl;
            // A snippet without a traceable source is not usable web evidence.
            if (line.length() >= 35 && !line.startsWith("Домен:") && !attributedUrl.isEmpty())
                out.add(new Finding(line, attributedUrl));
        }
        return out;
    }

    /** Use the report URL list only if it identifies exactly one valid source. */
    private static String singleUnambiguousUrl(String[] urls) {
        String only = "";
        if (urls == null) return "";
        for (String raw : urls) {
            String valid = safeUrl(raw);
            if (valid.isEmpty()) continue;
            if (only.isEmpty()) only = valid;
            else if (!only.equals(valid)) return "";
        }
        return only;
    }

    private static JSONArray readArray(String value) {
        try { return new JSONArray(value); } catch (Exception ignored) { return new JSONArray(); }
    }

    private static JSONArray toJsonArray(List<String> values) {
        JSONArray out = new JSONArray();
        if (values != null) for (String value : values) {
            String url = safeUrl(value);
            if (!url.isEmpty() && !containsString(out, url)) out.put(url);
        }
        return out;
    }

    private static boolean containsString(JSONArray values, String value) {
        for (int i = 0; i < values.length(); i++) {
            if (value.equals(values.optString(i))) return true;
        }
        return false;
    }

    private static String normalize(String text) {
        return (text == null ? "" : text).toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{Nd}]+", " ").trim();
    }

    private static String safeUrl(String url) {
        if (url == null) return "";
        String value = url.trim();
        try {
            URL parsed = new URL(value);
            String scheme = parsed.getProtocol();
            if (("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                    && parsed.getHost() != null && !parsed.getHost().trim().isEmpty()) {
                return value;
            }
        } catch (Exception ignored) {}
        return "";
    }

    private static String domain(String url) {
        try {
            String host = new URL(url).getHost().toLowerCase(Locale.ROOT);
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception ignored) { return ""; }
    }

    private static String firstSentence(String text) {
        String value = text == null ? "" : text.trim();
        Matcher m = Pattern.compile("^(.{1,88}?[.!?])(?:\\s|$)").matcher(value);
        return m.find() ? m.group(1) : value;
    }

    private static String shorten(String value, int limit) {
        if (value == null) return "";
        String x = value.trim();
        return x.length() > limit ? x.substring(0, limit - 1) + "…" : x;
    }

    public static synchronized String summaryText(Context context) {
        SharedPreferences p = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONArray observations = readArray(p.getString(OBSERVATIONS, "[]"));
        JSONArray clusters = readArray(p.getString(CLUSTERS, "[]"));
        StringBuilder out = new StringBuilder();
        out.append("Предварительных наблюдений: ").append(observations.length())
           .append(" · тематических кластеров: ").append(clusters.length()).append("\n");
        if (clusters.length() == 0) {
            out.append("\nПока нет сохранённых выводов. После веб-исследования сюда попадут пригодные фрагменты, даже если они ещё не доказывают общий вывод.");
            return out.toString();
        }
        List<JSONObject> ordered = new ArrayList<>();
        for (int i = 0; i < clusters.length(); i++) {
            JSONObject c = clusters.optJSONObject(i);
            if (c != null) ordered.add(c);
        }
        Collections.sort(ordered, (a, b) -> Long.compare(b.optLong("updatedAt"), a.optLong("updatedAt")));
        int shown = 0;
        for (JSONObject c : ordered) {
            if (shown++ >= 8) break;
            JSONArray members = c.optJSONArray("members");
            out.append("\n\n▸ ").append(c.optString("label", "Тематический кластер"))
               .append(" (").append(members == null ? 0 : members.length()).append(" наблюдений)");
            int added = 0;
            if (members != null) {
                for (int j = members.length() - 1; j >= 0 && added < 3; j--) {
                    String id = members.optString(j);
                    JSONObject item = findObservation(observations, id);
                    if (item == null) continue;
                    out.append("\n  • ").append(item.optString("text"));
                    out.append("\n    ").append(item.optString("status", "PRELIMINARY"))
                       .append(" · уверенность ").append(Math.round(item.optDouble("confidence", 0.25) * 100)).append("%");
                    if ("AGENT_PRELIMINARY_CONCLUSION".equals(item.optString("origin"))) {
                        out.append(" · предварительный вывод агента");
                    } else {
                        out.append(" · извлечённое наблюдение");
                    }
                    out.append("\n    Связь: ").append(item.optString("relationToCluster"))
                       .append(" · источник: ").append(item.optString("sourceDomain", "не определён"));
                    String conclusion = item.optString("preliminaryConclusion", "");
                    if (!conclusion.isEmpty()) out.append("\n    Предварительный вывод: ")
                            .append(conclusion.length() > 200 ? conclusion.substring(0, 197) + "…" : conclusion);
                    String basisId = item.optString("basisObservationId", "");
                    if (!basisId.isEmpty()) out.append("\n    Сопоставлено с observation: ").append(basisId);
                    String basis = item.optString("basis", "");
                    if (!basis.isEmpty()) out.append("\n    Основание: ")
                            .append(basis.length() > 160 ? basis.substring(0, 157) + "…" : basis);
                    JSONArray evidence = item.optJSONArray("evidenceUrls");
                    if (evidence != null && evidence.length() > 0) {
                        out.append("\n    Ссылок-оснований: ").append(evidence.length());
                    }
                    added++;
                }
            }
        }
        if (clusters.length() > 8) out.append("\n\nПоказаны 8 последних кластеров; остальные сохранены.");
        out.append("\n\nВсе записи предварительные. Связь означает тематическое сходство, а не подтверждённую истинность.");
        return out.toString();
    }

    private static JSONObject findObservation(JSONArray observations, String id) {
        for (int i = observations.length() - 1; i >= 0; i--) {
            JSONObject o = observations.optJSONObject(i);
            if (o != null && id.equals(o.optString("id"))) return o;
        }
        return null;
    }
}
