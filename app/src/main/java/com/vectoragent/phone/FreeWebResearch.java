package com.vectoragent.phone;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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
 * Zero-API-key web research. Uses public search result HTML plus public pages,
 * then builds an extractive report locally. No model API or secret is needed.
 *
 * Search engines and websites can rate-limit, change markup or block automated requests.
 * Therefore this is a best-effort fallback and must never promote a factual memory item.
 */
public final class FreeWebResearch {
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36 VectorAgentPhone/3.6";
    private static final int CONNECT_TIMEOUT_MS = 9000;
    private static final int READ_TIMEOUT_MS = 12000;
    private static final int MAX_SEARCH_BYTES = 450_000;
    private static final int MAX_PAGE_BYTES = 400_000;
    private static final int MAX_SOURCE_PAGES = 4;

    public static final class Result {
        public final String report;
        public final String[] urls;
        public final int fetchedPages;
        Result(String report, String[] urls, int fetchedPages) {
            this.report = report; this.urls = urls; this.fetchedPages = fetchedPages;
        }
    }

    private static final class SearchHit {
        String title, url, snippet;
        SearchHit(String title, String url, String snippet) {
            this.title = title; this.url = url; this.snippet = snippet;
        }
    }

    private static final class Source {
        String title, url, domain, snippet, extracted;
        Source(String title, String url, String domain, String snippet, String extracted) {
            this.title = title; this.url = url; this.domain = domain;
            this.snippet = snippet; this.extracted = extracted;
        }
    }

    private FreeWebResearch() {}

    public static Result research(String question) throws IOException {
        String q = question == null ? "" : question.trim();
        if (q.length() < 5) throw new IOException("Вопрос слишком короткий для поиска");

        List<SearchHit> hits = new ArrayList<>();
        IOException searchFailure = null;
        try {
            hits.addAll(searchDuckDuckGo(q));
        } catch (IOException e) {
            searchFailure = e;
        }

        // Wikimedia is a stable, public, keyless fallback if the HTML search provider is blocked.
        if (hits.size() < 3) {
            try { addWikipediaHits(q, hits); }
            catch (IOException e) { if (hits.isEmpty() && searchFailure == null) searchFailure = e; }
        }

        hits = dedupeHits(hits);
        if (hits.isEmpty()) {
            String message = "Бесплатный поиск не вернул доступных результатов. Возможны блокировка поисковой выдачи, отсутствие сети или изменение HTML-разметки.";
            if (searchFailure != null) message += " Причина: " + safeMessage(searchFailure);
            throw new IOException(message);
        }

        List<Source> sources = new ArrayList<>();
        Set<String> seenDomains = new LinkedHashSet<>();
        int fetched = 0;
        for (SearchHit hit : hits) {
            if (sources.size() >= MAX_SOURCE_PAGES) break;
            String domain = domain(hit.url);
            if (domain.isEmpty() || domain.contains("duckduckgo.com")) continue;
            if (!seenDomains.add(domain)) continue;

            String pageText = "";
            String pageTitle = hit.title;
            try {
                String html = get(hit.url, MAX_PAGE_BYTES);
                pageTitle = extractTitle(html, hit.title);
                pageText = extractReadableText(html);
                fetched++;
            } catch (IOException ignored) {
                // Search snippets are still useful evidence, but report them as snippets, not full-page extracts.
            }

            String extract = chooseSentences(q, pageText, 2);
            String snippet = clean(hit.snippet);
            if (extract.isEmpty() && snippet.isEmpty()) continue;
            sources.add(new Source(pageTitle, hit.url, domain,
                    snippet, extract));
        }

        // If domain de-duplication or page failures left no readable sources, preserve search snippets.
        if (sources.isEmpty()) {
            for (SearchHit hit : hits) {
                String domain = domain(hit.url);
                if (domain.isEmpty() || domain.contains("duckduckgo.com")) continue;
                sources.add(new Source(hit.title, hit.url, domain, clean(hit.snippet), ""));
                if (sources.size() >= MAX_SOURCE_PAGES) break;
            }
        }
        if (sources.isEmpty()) throw new IOException("Поиск нашёл страницы, но из них не удалось извлечь доступные сведения");

        String report = buildReport(q, sources, fetched);
        List<String> urls = new ArrayList<>();
        for (Source source : sources) if (!urls.contains(source.url)) urls.add(source.url);
        return new Result(report, urls.toArray(new String[0]), fetched);
    }

    private static List<SearchHit> searchDuckDuckGo(String query) throws IOException {
        String url = "https://html.duckduckgo.com/html/?q=" + encode(query);
        String html = get(url, MAX_SEARCH_BYTES);
        List<SearchHit> out = new ArrayList<>();
        Pattern anchors = Pattern.compile("(?is)<a\\b([^>]*?)>(.*?)</a>");
        Matcher m = anchors.matcher(html);
        while (m.find() && out.size() < 12) {
            String attrs = m.group(1);
            if (!attrs.toLowerCase(Locale.ROOT).contains("result__a")) continue;
            String href = attribute(attrs, "href");
            String title = clean(stripTags(m.group(2)));
            String resolved = unwrapSearchUrl(href);
            if (!isHttp(resolved) || title.isEmpty() || isSearchProvider(resolved)) continue;
            String snippet = findSnippet(html, m.end());
            out.add(new SearchHit(title, resolved, snippet));
        }
        return out;
    }

    private static String findSnippet(String html, int from) {
        int end = Math.min(html.length(), from + 3500);
        String window = html.substring(from, end);
        Matcher m = Pattern.compile("(?is)<(?:a|div|span)[^>]*class=[\"'][^\"']*result__snippet[^\"']*[\"'][^>]*>(.*?)</(?:a|div|span)>").matcher(window);
        return m.find() ? clean(stripTags(m.group(1))) : "";
    }

    private static void addWikipediaHits(String query, List<SearchHit> hits) throws IOException {
        String url = "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch="
                + encode(query) + "&format=json&utf8=1&srlimit=5";
        String json = get(url, 250_000);
        JSONObject root = new JSONObject(json);
        JSONObject queryObj = root.optJSONObject("query");
        JSONArray arr = queryObj == null ? null : queryObj.optJSONArray("search");
        if (arr == null) return;
        for (int i = 0; i < arr.length() && hits.size() < 12; i++) {
            JSONObject item = arr.optJSONObject(i);
            if (item == null) continue;
            String title = item.optString("title", "").trim();
            if (title.isEmpty()) continue;
            String pageUrl = "https://en.wikipedia.org/wiki/" + encode(title.replace(' ', '_')).replace("%2F", "/");
            String snippet = clean(stripTags(item.optString("snippet", "")));
            hits.add(new SearchHit(title, pageUrl, snippet));
        }
    }

    private static List<SearchHit> dedupeHits(List<SearchHit> input) {
        List<SearchHit> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (SearchHit h : input) {
            String key = h.url == null ? "" : h.url.replaceAll("[?#].*$", "").replaceAll("/+$", "");
            if (key.isEmpty() || !seen.add(key)) continue;
            out.add(h);
        }
        return out;
    }

    private static String buildReport(String question, List<Source> sources, int fetched) {
        StringBuilder out = new StringBuilder();
        out.append("БЕСПЛАТНОЕ ВЕБ-ИССЛЕДОВАНИЕ (локальный синтез без языковой модели)\n\n");
        out.append("Вопрос: ").append(question).append("\n\n");
        out.append("Источников с разными доменами: ").append(sources.size())
           .append("; страниц удалось загрузить: ").append(fetched).append(".\n");
        out.append("Метод: публичная поисковая выдача и открытые страницы; отобраны фрагменты, совпадающие с терминами вопроса. ")
           .append("Это предварительный обзор, а не автоматическая проверка истинности. Совпадение результатов не гарантирует независимость источников.\n");

        for (int i = 0; i < sources.size(); i++) {
            Source s = sources.get(i);
            out.append("\n").append(i + 1).append(". ").append(s.title.isEmpty() ? s.domain : s.title)
               .append("\nДомен: ").append(s.domain).append("\nURL: ").append(s.url).append("\n");
            if (!s.extracted.isEmpty()) {
                out.append("Фрагменты страницы:\n").append(s.extracted).append("\n");
            } else if (!s.snippet.isEmpty()) {
                out.append("Фрагмент поисковой выдачи (сама страница не прочитана):\n")
                   .append(s.snippet).append("\n");
            } else {
                out.append("Текст страницы недоступен; ссылка требует ручной проверки.\n");
            }
        }

        out.append("\nЧто пока НЕ установлено автоматически:\n")
           .append("• подтверждают ли источники один и тот же точный тезис;\n")
           .append("• независимы ли авторы и первичные данные;\n")
           .append("• насколько свежи и применимы сведения к конкретным условиям.\n");
        out.append("\nСледующий шаг: открой первоисточники, сравни даты, методики и определения. ")
           .append("Сохрани подтверждение или противоречие отдельно; этот отчёт сам по себе не меняет фактическую память.");
        return out.toString();
    }

    private static String chooseSentences(String query, String text, int max) {
        if (text == null || text.trim().isEmpty()) return "";
        List<String> terms = terms(query);
        String[] pieces = text.split("(?<=[.!?])\\s+|\\n+");
        List<String> candidates = new ArrayList<>();
        for (String p : pieces) {
            String x = clean(p);
            if (x.length() < 70 || x.length() > 550) continue;
            if (candidates.contains(x)) continue;
            candidates.add(x);
        }
        final List<String> allTerms = terms;
        Collections.sort(candidates, new Comparator<String>() {
            @Override public int compare(String a, String b) {
                return Integer.compare(score(b, allTerms), score(a, allTerms));
            }
        });
        StringBuilder out = new StringBuilder();
        int added = 0;
        for (String sentence : candidates) {
            if (score(sentence, terms) == 0 && added > 0) continue;
            if (added > 0) out.append("\n• ");
            else out.append("• ");
            out.append(sentence);
            added++;
            if (added >= max) break;
        }
        return out.toString();
    }

    private static int score(String text, List<String> terms) {
        String n = normalize(text);
        int score = 0;
        for (String term : terms) if (n.contains(term)) score++;
        return score;
    }

    private static List<String> terms(String text) {
        String n = normalize(text);
        String[] all = n.split("\\s+");
        Set<String> stop = new HashSet<>(Arrays.asList(
                "который","которая","которые","когда","тогда","чтобы","этого","этому","этими","также",
                "между","через","после","перед","какие","какой","какая","может","могут","нужно",
                "система","системы","агент","агента","исследование","исследуй","найти","найди",
                "this","that","with","from","into","what","when","where","which","will","would",
                "have","has","been","being","their","there","about","should","could","than","then",
                "your","you","they","them","does","using","used","ways","best","real","current",
                "how","why","for","and","the","are","was","were","is","not","but","into"));
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String t : all) if (t.length() >= 4 && !stop.contains(t)) result.add(t);
        return new ArrayList<>(result);
    }

    private static String normalize(String text) {
        return (text == null ? "" : text).toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{Nd}]+", " ").trim();
    }

    private static String extractTitle(String html, String fallback) {
        Matcher m = Pattern.compile("(?is)<title[^>]*>(.*?)</title>").matcher(html);
        return m.find() ? clean(stripTags(m.group(1))) : fallback;
    }

    private static String extractReadableText(String html) {
        String x = html.replaceAll("(?is)<(script|style|noscript|svg|nav|footer|header|aside)[^>]*>.*?</\\1>", " ");
        x = x.replaceAll("(?is)<!--.*?-->", " ");
        x = x.replaceAll("(?i)</(p|li|h1|h2|h3|h4|blockquote|article|section|br|tr)>", " . ");
        x = stripTags(x);
        x = decodeEntities(x);
        StringBuilder cleanLines = new StringBuilder();
        for (String line : x.split("\\n+")) {
            String s = clean(line);
            if (s.length() >= 50) cleanLines.append(s).append("\n");
        }
        String result = cleanLines.toString();
        return result.length() > 25_000 ? result.substring(0,25_000) : result;
    }

    private static String stripTags(String html) {
        return html == null ? "" : html.replaceAll("(?s)<[^>]*>", " ");
    }

    private static String decodeEntities(String s) {
        return s.replace("&nbsp;", " ").replace("&#160;", " ")
                .replace("&amp;", "&").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&#x27;", "'").replace("&ndash;", "–").replace("&mdash;", "—");
    }

    private static String clean(String s) {
        return decodeEntities(s == null ? "" : s.replaceAll("<[^>]*>", " "))
                .replaceAll("\\s+", " ").trim();
    }

    private static String attribute(String attrs, String name) {
        Matcher m = Pattern.compile("(?is)\\b" + Pattern.quote(name) + "=[\"'](.*?)[\"']").matcher(attrs);
        return m.find() ? decodeEntities(m.group(1)) : "";
    }

    private static String unwrapSearchUrl(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        String value = decodeEntities(raw);
        try {
            URL u = new URL(value.startsWith("//") ? "https:" + value : value);
            String q = u.getQuery();
            if (q != null) {
                for (String part : q.split("&")) {
                    int i = part.indexOf('=');
                    if (i <= 0) continue;
                    String k = URLDecoder.decode(part.substring(0, i), "UTF-8");
                    if ("uddg".equals(k)) return URLDecoder.decode(part.substring(i + 1), "UTF-8");
                }
            }
        } catch (Exception ignored) {}
        return value;
    }

    private static String domain(String url) {
        try {
            String host = new URL(url).getHost().toLowerCase(Locale.ROOT);
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception e) { return ""; }
    }

    private static boolean isSearchProvider(String url) {
        String d = domain(url);
        return d.contains("duckduckgo.com") || d.contains("wikipedia.org") && url.contains("/w/api.php");
    }

    private static boolean isHttp(String url) {
        return url != null && (url.startsWith("https://") || url.startsWith("http://"));
    }

    private static String get(String url, int maxBytes) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(CONNECT_TIMEOUT_MS);
        c.setReadTimeout(READ_TIMEOUT_MS);
        c.setRequestProperty("User-Agent", USER_AGENT);
        c.setRequestProperty("Accept-Language", "en-US,en;q=0.8,ru;q=0.7");
        c.setInstanceFollowRedirects(true);
        c.setRequestMethod("GET");
        try {
            int code = c.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
            if (code < 200 || code >= 300) throw new IOException("HTTP " + code + " для " + domain(url));
            StringBuilder out = new StringBuilder();
            try (InputStream in = stream) {
                byte[] buf = new byte[4096];
                int count, total = 0;
                while ((count = in.read(buf)) != -1 && total < maxBytes) {
                    int use = Math.min(count, maxBytes - total);
                    out.append(new String(buf, 0, use, StandardCharsets.UTF_8));
                    total += use;
                }
            }
            return out.toString();
        } finally {
            c.disconnect();
        }
    }

    private static String encode(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); }
        catch (Exception e) { return s; }
    }

    private static String safeMessage(IOException e) {
        String m = e.getMessage();
        if (m == null || m.trim().isEmpty()) return e.getClass().getSimpleName();
        return m.length() > 180 ? m.substring(0,180) + "…" : m;
    }
}
