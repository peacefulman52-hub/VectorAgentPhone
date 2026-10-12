package com.vectoragent.phone;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URL;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Durable store for cautious, extractive research observations.
 *
 * Every distinct statement remains its own TENTATIVE item. Clustering links related
 * statements without replacing them with one merged claim. This store never changes
 * MemoryStore's facts or resolves its conflicts.
 */
public final class ResearchConclusionStore {
    private static final String PREFS = "vector_research_conclusions";
    private static final String CLAIMS = "claims";
    private static final String CLUSTERS = "clusters";
    private static final String RELATIONS = "relations";
    private static final int MAX_CLAIMS = 500;
    private static final double RELATED_THRESHOLD = 0.36;

    private static final Set<String> STOP = new HashSet<>(Arrays.asList(
            "который","которая","которые","когда","тогда","чтобы","этого","этому","этими",
            "также","между","через","после","перед","какие","какой","какая","может","могут",
            "нужно","система","системы","агент","агента","исследование","исследуй","найти",
            "найди","предложи","сравни","лучшие","лучший","должен","должна","есть","быть",
            "это","этот","эта","эти","или","для","при","как","что","где","когда","почему",
            "and","the","for","with","from","into","what","when","where","which","will",
            "would","have","has","been","being","their","there","about","should","could",
            "than","then","your","you","they","them","does","using","used","ways","best",
            "real","current","how","why","are","was","were","is","not","but","this","that",
            "can","may","its","our","all","any","per","via"
    ));

    public static final class IngestSummary {
        public int observations;
        public int newConclusions;
        public int addedToExistingClusters;
        public int reinforced;
        public int newClusters;
        public int possibleContradictions;
        public int totalClusters;

        public String shortText() {
            return "Предварительных наблюдений: " + observations
                    + "; новых выводов: " + newConclusions
                    + "; добавлено в существующие кластеры: " + addedToExistingClusters
                    + "; повторно встречено: " + reinforced
                    + "; возможных противоречий для проверки: " + possibleContradictions
                    + "; кластеров всего: " + totalClusters + ".";
        }
    }

    private static final class Observation {
        String text;
        String url;
        String title;
        Observation(String text, String url, String title) {
            this.text = text; this.url = url; this.title = title;
        }
    }

    private ResearchConclusionStore() {}

    public static synchronized IngestSummary ingestReport(Context context, String topicId,
            String title, String question, String report, String[] fallbackUrls) {
        IngestSummary summary = new IngestSummary();
        if (report == null || report.trim().isEmpty()) {
            summary.totalClusters = clusterCount(context);
            return summary;
        }

        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONArray claims = readArray(prefs, CLAIMS);
        JSONArray clusters = readArray(prefs, CLUSTERS);
        JSONArray relations = readArray(prefs, RELATIONS);
        List<Observation> observations = extractObservations(report, fallbackUrls);

        for (Observation observation : observations) {
            String text = clean(observation.text);
            String normalized = normalizeText(text);
            if (text.length() < 45 || text.length() > 550 || usefulTokens(text).size() < 3) continue;

            summary.observations++;
            JSONObject exact = findExact(claims, normalized);
            if (exact != null) {
                boolean addedSource = addSource(exact, observation.url);
                exact.put("occurrences", exact.optInt("occurrences", 1) + 1);
                exact.put("updatedAt", System.currentTimeMillis());
                exact.put("lastQuestion", safe(question));
                exact.put("lastTopicId", safe(topicId));
                exact.put("confidence", confidenceForSources(exact.optJSONArray("sourceUrls")));
                if (addedSource) summary.reinforced++;
                else summary.reinforced++;
                continue;
            }

            JSONObject nearest = null;
            double bestScore = 0.0;
            Set<String> incomingTokens = usefulTokens(text);
            for (int i = 0; i < claims.length(); i++) {
                JSONObject prior = claims.optJSONObject(i);
                if (prior == null) continue;
                Set<String> priorTokens = usefulTokens(prior.optString("text"));
                int shared = intersectionSize(incomingTokens, priorTokens);
                double score = cosine(incomingTokens, priorTokens);
                if (shared >= 2 && score >= RELATED_THRESHOLD && score > bestScore) {
                    bestScore = score;
                    nearest = prior;
                }
            }

            long now = System.currentTimeMillis();
            String id = "rc-" + now + "-" + claims.length();
            String clusterId;
            String relationType = "NEW_CLUSTER";
            if (nearest != null) {
                clusterId = nearest.optString("clusterId");
                relationType = possibleContradiction(text, nearest.optString("text"))
                        ? "POSSIBLE_CONTRADICTION" : "RELATED_BUT_DISTINCT";
                JSONObject cluster = findCluster(clusters, clusterId);
                if (cluster == null) {
                    clusterId = createCluster(clusters, text, now);
                    relationType = "NEW_CLUSTER";
                    summary.newClusters++;
                } else {
                    JSONArray members = cluster.optJSONArray("memberIds");
                    if (members == null) members = new JSONArray();
                    members.put(id);
                    cluster.put("memberIds", members);
                    cluster.put("claimCount", members.length());
                    cluster.put("updatedAt", now);
                    summary.addedToExistingClusters++;
                    if ("POSSIBLE_CONTRADICTION".equals(relationType)) summary.possibleContradictions++;
                    addRelation(relations, nearest.optString("id"), id, relationType, bestScore, now);
                }
            } else {
                clusterId = createCluster(clusters, text, now);
                summary.newClusters++;
            }

            JSONObject claim = new JSONObject();
            claim.put("id", id);
            claim.put("clusterId", clusterId);
            claim.put("text", text);
            claim.put("normalized", normalized);
            claim.put("status", "TENTATIVE");
            claim.put("kind", "EXTRACTED_OBSERVATION");
            claim.put("confidence", 0.22);
            claim.put("confidenceMeaning", "heuristic provisional score; not a probability of truth");
            claim.put("support", 1);
            claim.put("occurrences", 1);
            claim.put("topicId", safe(topicId));
            claim.put("title", safe(title));
            claim.put("question", safe(question));
            claim.put("createdAt", now);
            claim.put("updatedAt", now);
            claim.put("relationToNearest", relationType);
            if (nearest != null && !clusterId.isEmpty()) {
                claim.put("relatedClaimId", nearest.optString("id"));
                claim.put("similarity", bestScore);
            }
            JSONArray sourceUrls = new JSONArray();
            if (isHttp(observation.url)) sourceUrls.put(observation.url);
            claim.put("sourceUrls", sourceUrls);
            claim.put("sourceDomains", domains(sourceUrls));
            claims.put(claim);
            summary.newConclusions++;
        }

        pruneClaims(claims, clusters, relations);
        prefs.edit()
                .putString(CLAIMS, claims.toString())
                .putString(CLUSTERS, clusters.toString())
                .putString(RELATIONS, relations.toString())
                .putLong("last_ingest_at", System.currentTimeMillis())
                .apply();
        summary.totalClusters = clusters.length();
        return summary;
    }

    private static List<Observation> extractObservations(String report, String[] fallbackUrls) {
        List<Observation> result = new ArrayList<>();
        String[] lines = report.split("\\r?\\n");
        String currentUrl = "";
        String currentTitle = "";
        int sourceIndex = -1;
        int mode = 0; // 0=outside evidence, 1=page sentence bullets, 2=one search snippet next
        for (String original : lines) {
            String line = original == null ? "" : original.trim();
            if (line.matches("^\\d+\\.\\s+.+")) {
                sourceIndex++;
                currentTitle = line.replaceFirst("^\\d+\\.\\s+", "");
                currentUrl = sourceIndex >= 0 && fallbackUrls != null && sourceIndex < fallbackUrls.length
                        ? safe(fallbackUrls[sourceIndex]) : "";
                mode = 0;
                continue;
            }
            if (line.startsWith("URL:")) {
                String candidate = line.substring(4).trim();
                if (isHttp(candidate)) currentUrl = candidate;
                mode = 0;
                continue;
            }
            if (line.startsWith("Фрагменты страницы:")) { mode = 1; continue; }
            if (line.startsWith("Фрагмент поисковой выдачи")) { mode = 2; continue; }
            if (line.startsWith("Текст страницы недоступен")) { mode = 0; continue; }
            if (line.startsWith("Что пока НЕ установлено") || line.startsWith("Следующий шаг:")) break;
            if (line.isEmpty()) {
                if (mode == 2) mode = 0;
                continue;
            }
            if (mode == 1) {
                if (line.startsWith("•")) {
                    String text = line.replaceFirst("^•\\s*", "").trim();
                    if (!text.isEmpty()) result.add(new Observation(text, currentUrl, currentTitle));
                } else if (!line.startsWith("Домен:") && !line.startsWith("Источники:")) {
                    mode = 0;
                }
            } else if (mode == 2) {
                String text = line.replaceFirst("^•\\s*", "").trim();
                if (text.length() >= 45) result.add(new Observation(text, currentUrl, currentTitle));
                mode = 0;
            }
            if (result.size() >= 80) break;
        }
        return result;
    }

    private static String createCluster(JSONArray clusters, String firstText, long now) throws Exception {
        String id = "cluster-" + now + "-" + clusters.length();
        JSONObject cluster = new JSONObject();
        JSONArray members = new JSONArray();
        // The first member is appended by the caller immediately after this method.
        cluster.put("id", id);
        cluster.put("label", firstText.length() > 130 ? firstText.substring(0, 127) + "…" : firstText);
        cluster.put("memberIds", members);
        cluster.put("claimCount", 0);
        cluster.put("createdAt", now);
        cluster.put("updatedAt", now);
        clusters.put(cluster);
        return id;
    }

    private static JSONObject findExact(JSONArray claims, String normalized) {
        for (int i = 0; i < claims.length(); i++) {
            JSONObject o = claims.optJSONObject(i);
            if (o != null && normalized.equals(o.optString("normalized"))) return o;
        }
        return null;
    }

    private static JSONObject findCluster(JSONArray clusters, String id) {
        for (int i = 0; i < clusters.length(); i++) {
            JSONObject o = clusters.optJSONObject(i);
            if (o != null && id.equals(o.optString("id"))) return o;
        }
        return null;
    }

    private static boolean addSource(JSONObject claim, String url) throws Exception {
        if (!isHttp(url)) return false;
        JSONArray sources = claim.optJSONArray("sourceUrls");
        if (sources == null) sources = new JSONArray();
        for (int i = 0; i < sources.length(); i++) if (url.equals(sources.optString(i))) return false;
        sources.put(url);
        claim.put("sourceUrls", sources);
        claim.put("sourceDomains", domains(sources));
        claim.put("support", Math.max(1, domains(sources).length()));
        return true;
    }

    private static JSONArray domains(JSONArray urls) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (urls != null) for (int i = 0; i < urls.length(); i++) {
            try {
                String d = new URL(urls.optString(i)).getHost().toLowerCase(Locale.ROOT);
                if (d.startsWith("www.")) d = d.substring(4);
                if (!d.isEmpty()) set.add(d);
            } catch (Exception ignored) {}
        }
        JSONArray out = new JSONArray();
        for (String d : set) out.put(d);
        return out;
    }

    private static double confidenceForSources(JSONArray sourceUrls) {
        int domains = domains(sourceUrls).length();
        // Evidence independence is not established, so this score stays deliberately low.
        return Math.min(0.42, 0.22 + Math.max(0, domains - 1) * 0.05);
    }

    private static void addRelation(JSONArray relations, String from, String to,
                                    String type, double score, long now) throws Exception {
        for (int i = 0; i < relations.length(); i++) {
            JSONObject existing = relations.optJSONObject(i);
            if (existing == null) continue;
            if ((from.equals(existing.optString("from")) && to.equals(existing.optString("to")))
                    || (to.equals(existing.optString("from")) && from.equals(existing.optString("to")))) return;
        }
        JSONObject relation = new JSONObject();
        relation.put("from", from);
        relation.put("to", to);
        relation.put("type", type);
        relation.put("similarity", score);
        relation.put("createdAt", now);
        relations.put(relation);
    }

    private static void pruneClaims(JSONArray claims, JSONArray clusters, JSONArray relations) throws Exception {
        while (claims.length() > MAX_CLAIMS) claims.remove(0);
        Set<String> claimIds = new HashSet<>();
        for (int i = 0; i < claims.length(); i++) {
            JSONObject o = claims.optJSONObject(i);
            if (o != null) claimIds.add(o.optString("id"));
        }
        for (int i = clusters.length() - 1; i >= 0; i--) {
            JSONObject cluster = clusters.optJSONObject(i);
            if (cluster == null) { clusters.remove(i); continue; }
            JSONArray members = cluster.optJSONArray("memberIds");
            JSONArray keep = new JSONArray();
            if (members != null) for (int j = 0; j < members.length(); j++) {
                String id = members.optString(j);
                if (claimIds.contains(id)) keep.put(id);
            }
            cluster.put("memberIds", keep);
            cluster.put("claimCount", keep.length());
            if (keep.length() == 0) clusters.remove(i);
        }
        for (int i = relations.length() - 1; i >= 0; i--) {
            JSONObject relation = relations.optJSONObject(i);
            if (relation == null || !claimIds.contains(relation.optString("from"))
                    || !claimIds.contains(relation.optString("to"))) relations.remove(i);
        }
    }

    public static synchronized int clusterCount(Context context) {
        SharedPreferences p = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return readArray(p, CLUSTERS).length();
    }

    public static synchronized String summaryText(Context context) {
        SharedPreferences p = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONArray claims = readArray(p, CLAIMS);
        JSONArray clusters = readArray(p, CLUSTERS);
        JSONArray relations = readArray(p, RELATIONS);
        List<JSONObject> sorted = new ArrayList<>();
        int contradictions = 0;
        for (int i = 0; i < clusters.length(); i++) {
            JSONObject c = clusters.optJSONObject(i);
            if (c != null) sorted.add(c);
        }
        for (int i = 0; i < relations.length(); i++) {
            JSONObject r = relations.optJSONObject(i);
            if (r != null && "POSSIBLE_CONTRADICTION".equals(r.optString("type"))) contradictions++;
        }
        Collections.sort(sorted, new Comparator<JSONObject>() {
            @Override public int compare(JSONObject a, JSONObject b) {
                return Long.compare(b.optLong("updatedAt"), a.optLong("updatedAt"));
            }
        });
        StringBuilder out = new StringBuilder();
        out.append("КЛАСТЕРЫ ПРЕДВАРИТЕЛЬНЫХ ВЫВОДОВ\\n");
        out.append("Отдельных наблюдений: ").append(claims.length())
                .append(" · кластеров: ").append(clusters.length())
                .append(" · возможных противоречий: ").append(contradictions).append("\\n");
        out.append("Все новые записи имеют статус TENTATIVE и не меняют фактическую память. Похожие утверждения связаны, но не сливаются.\\n");
        if (claims.length() == 0) {
            out.append("\\nПока нет предварительных выводов. Они будут добавлены из содержательных фрагментов успешного исследования.");
            return out.toString();
        }
        int shown = Math.min(8, sorted.size());
        for (int i = 0; i < shown; i++) {
            JSONObject cluster = sorted.get(i);
            out.append("\\n").append(i + 1).append(". ").append(cluster.optString("label"))
                    .append("\\n   Наблюдений в кластере: ").append(cluster.optInt("claimCount")).append("\\n");
            JSONArray members = cluster.optJSONArray("memberIds");
            int printed = 0;
            if (members != null) for (int j = members.length() - 1; j >= 0 && printed < 2; j--) {
                JSONObject claim = findClaim(claims, members.optString(j));
                if (claim == null) continue;
                String statement = claim.optString("text");
                out.append("   • [TENTATIVE, уверенность ")
                        .append(Math.round(claim.optDouble("confidence", 0.22) * 100)).append("%] ")
                        .append(statement.length() > 180 ? statement.substring(0, 177) + "…" : statement)
                        .append("\\n");
                printed++;
            }
        }
        if (sorted.size() > shown) out.append("\\nПоказаны последние ").append(shown)
                .append(" кластеров из ").append(sorted.size()).append(".");
        return out.toString();
    }

    private static JSONObject findClaim(JSONArray claims, String id) {
        for (int i = 0; i < claims.length(); i++) {
            JSONObject o = claims.optJSONObject(i);
            if (o != null && id.equals(o.optString("id"))) return o;
        }
        return null;
    }

    private static JSONArray readArray(SharedPreferences p, String key) {
        try { return new JSONArray(p.getString(key, "[]")); }
        catch (Exception ignored) { return new JSONArray(); }
    }

    private static Set<String> usefulTokens(String text) {
        Set<String> result = new LinkedHashSet<>();
        String cleaned = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{Nd}]+", " ").trim();
        if (cleaned.isEmpty()) return result;
        for (String raw : cleaned.split("\\s+")) {
            String token = stem(raw);
            if (token.length() < 3 || STOP.contains(token)) continue;
            result.add(token);
        }
        return result;
    }

    private static String stem(String word) {
        if (word.length() < 5) return word;
        String[] suffixes = {
                "ирования","ирование","ированием","ирований","иями","ями","ами","ениях","ение",
                "ения","ению","ений","ательным","ательными","ательный","ательность","ировать",
                "ировать","ировать","ственных","ственный","ственная","ственное","ственных",
                "ующие","ующий","ующая","ованы","овать","ировать","иями","ями","его","ому",
                "ыми","ими","ого","ему","ее","ие","ые","ая","яя","ое","ий","ый","ой",
                "ам","ям","ом","ем","ах","ях","ы","и","а","я","о","е","у","ю",
                "ing","ers","ies","ed","es","s"
        };
        for (String suffix : suffixes) {
            if (word.length() - suffix.length() >= 3 && word.endsWith(suffix)) {
                return word.substring(0, word.length() - suffix.length());
            }
        }
        return word;
    }

    private static int intersectionSize(Set<String> a, Set<String> b) {
        int total = 0;
        for (String token : a) if (b.contains(token)) total++;
        return total;
    }

    private static double cosine(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        return intersectionSize(a, b) / Math.sqrt((double) a.size() * (double) b.size());
    }

    private static boolean possibleContradiction(String a, String b) {
        String x = normalizeText(a), y = normalizeText(b);
        boolean negX = containsNegation(x), negY = containsNegation(y);
        if (negX != negY) return true;
        Set<String> tx = usefulTokens(a), ty = usefulTokens(b);
        if (intersectionSize(tx, ty) < 2) return false;
        List<String> nx = numbers(x), ny = numbers(y);
        if (!nx.isEmpty() && !ny.isEmpty() && !nx.equals(ny)) return true;
        return false;
    }

    private static boolean containsNegation(String value) {
        return value.matches(".*(?:^|\\s)(?:не|нет|без|ни|никогда|отсутствует|отсутствуют)(?:$|\\s).*");
    }

    private static List<String> numbers(String value) {
        List<String> values = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b\\d+(?:[.,]\\d+)?\\b").matcher(value);
        while (m.find()) values.add(m.group().replace(',', '.'));
        return values;
    }

    private static String normalizeText(String text) {
        return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{Nd}]+", " ").trim()
                .replaceAll("\\s+", " ");
    }

    private static String clean(String s) {
        return (s == null ? "" : s).replaceAll("<[^>]*>", " ")
                .replaceAll("\\s+", " ").trim();
    }

    private static String safe(String s) { return s == null ? "" : s.trim(); }

    private static boolean isHttp(String url) {
        return url != null && (url.startsWith("https://") || url.startsWith("http://"));
    }
}
