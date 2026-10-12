package com.vectoragent.phone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Offline regression tests: these tests never call the public internet. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ResearchKnowledgeStoreTest {
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("vector_research_knowledge", Context.MODE_PRIVATE)
                .edit().clear().commit();
        context.getSharedPreferences("vector_initiative_agenda", Context.MODE_PRIVATE)
                .edit().clear().commit();
        context.getSharedPreferences("vector_research_conclusions", Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    @Test
    public void extractsOnlyEvidenceBlocksAndKeepsEachSourceUrl() throws Exception {
        String report = "БЕСПЛАТНОЕ ВЕБ-ИССЛЕДОВАНИЕ\n"
                + "Вопрос: температура кипения воды\n"
                + "Метод: предварительный обзор\n\n"
                + "1. Первый источник\n"
                + "Домен: first.example\n"
                + "URL: https://first.example/water\n"
                + "Фрагменты страницы:\n"
                + "• Water boils at 100 degrees Celsius at sea-level atmospheric pressure.\n\n"
                + "Что пока НЕ установлено автоматически:\n"
                + "• This footer must not be stored as evidence.\n\n"
                + "2. Второй источник\n"
                + "Домен: second.example\n"
                + "URL: https://second.example/boiling\n"
                + "Фрагмент поисковой выдачи (сама страница не прочитана):\n"
                + "• Water reaches boiling at 100 degrees Celsius near sea-level atmospheric pressure.\n\n"
                + "Следующий шаг: manually compare primary sources.";

        int count = ResearchKnowledgeStore.ingestReport(
                context, "Точка кипения", "Исследуй точку кипения воды", report,
                new String[]{"https://first.example/water", "https://second.example/boiling"});

        assertEquals(2, count);
        JSONArray observations = observations();
        assertEquals(2, observations.length());
        assertEquals("https://first.example/water", observations.getJSONObject(0).getString("sourceUrl"));
        assertEquals("https://second.example/boiling", observations.getJSONObject(1).getString("sourceUrl"));
        assertFalse(observations.getJSONObject(0).getString("text").contains("footer must not"));
        assertFalse(observations.getJSONObject(1).getString("text").contains("manually compare"));
        assertTrue(observations.getJSONObject(1).has("preliminaryConclusion"));
        assertEquals("CANDIDATE", observations.getJSONObject(1).getString("conclusionStatus"));
    }

    @Test
    public void refusesToGuessAttributionWhenReportContainsSeveralUrlsButNoBlockUrl() throws Exception {
        String report = "1. Page without source URL\n"
                + "Фрагменты страницы:\n"
                + "• Water boils at 100 degrees Celsius at sea-level atmospheric pressure.\n";
        int count = ResearchKnowledgeStore.ingestReport(context, "Тема", "Вопрос", report,
                new String[]{"https://first.example/page", "https://second.example/page"});
        assertEquals(0, count);
        assertEquals(0, observations().length());
    }

    @Test
    public void singleSourceMayUseOneUnambiguousFallbackUrl() throws Exception {
        String report = "1. One source block\n"
                + "Фрагменты страницы:\n"
                + "• Water boils at 100 degrees Celsius at sea-level atmospheric pressure.\n";
        String url = "https://first.example/water";
        assertEquals(1, ResearchKnowledgeStore.ingestReport(
                context, "Точка кипения", "Вопрос", report, new String[]{url}));
        assertEquals(url, observations().getJSONObject(0).getString("sourceUrl"));
    }

    @Test
    public void singleUrlIsNotSmearedAcrossSeveralUnlabelledSourceBlocks() throws Exception {
        String report = "1. First source\n"
                + "Фрагменты страницы:\n"
                + "• Water boils at 100 degrees Celsius at sea-level atmospheric pressure.\n"
                + "2. Second source\n"
                + "Фрагменты страницы:\n"
                + "• Water reaches boiling at 100 degrees Celsius near sea-level atmospheric pressure.\n";
        assertEquals(0, ResearchKnowledgeStore.ingestReport(
                context, "Точка кипения", "Вопрос", report, new String[]{"https://first.example/water"}));
        assertEquals(0, observations().length());
    }

    @Test
    public void repeatedSameSourceObservationIsIdempotentAndKeepsRepeatCount() throws Exception {
        String report = oneSourceReport("https://first.example/water",
                "Water boils at 100 degrees Celsius at sea-level atmospheric pressure.");
        assertEquals(1, ResearchKnowledgeStore.ingestReport(context, "Точка кипения", "Вопрос", report,
                new String[]{"https://first.example/water"}));
        assertEquals(1, ResearchKnowledgeStore.ingestReport(context, "Точка кипения", "Вопрос", report,
                new String[]{"https://first.example/water"}));

        JSONArray observations = observations();
        assertEquals("One exact report from one URL must not create a second observation.", 1, observations.length());
        assertEquals(2, observations.getJSONObject(0).getInt("repeatCount"));
        assertEquals(1, new JSONArray(preferences().getString("clusters", "[]")).length());
    }

    @Test
    public void relatedButNonIdenticalFindingsShareAClusterAndRemainSeparate() throws Exception {
        String first = oneSourceReport("https://first.example/water",
                "Water boils at 100 degrees Celsius at sea-level atmospheric pressure.");
        String second = oneSourceReport("https://second.example/boiling",
                "Water reaches boiling at 100 degrees Celsius near sea-level atmospheric pressure.");
        ResearchKnowledgeStore.ingestReport(context, "Точка кипения", "Вопрос", first,
                new String[]{"https://first.example/water"});
        ResearchKnowledgeStore.ingestReport(context, "Точка кипения", "Вопрос", second,
                new String[]{"https://second.example/boiling"});

        JSONArray observations = observations();
        assertEquals(2, observations.length());
        JSONObject a = observations.getJSONObject(0);
        JSONObject b = observations.getJSONObject(1);
        assertNotEquals(a.getString("text"), b.getString("text"));
        assertEquals(a.getString("clusterId"), b.getString("clusterId"));
        assertEquals("RELATED_UNVERIFIED", b.getString("relationToCluster"));
        assertEquals(a.getString("id"), b.getString("basisObservationId"));
    }

    @Test
    public void differingNumbersStaySeparateAndAreMarkedForReview() throws Exception {
        String first = oneSourceReport("https://first.example/water",
                "Water boils at 100 degrees Celsius at sea-level atmospheric pressure.");
        String second = oneSourceReport("https://second.example/water",
                "Water boils at 80 degrees Celsius at sea-level atmospheric pressure.");
        ResearchKnowledgeStore.ingestReport(context, "Точка кипения", "Вопрос", first,
                new String[]{"https://first.example/water"});
        ResearchKnowledgeStore.ingestReport(context, "Точка кипения", "Вопрос", second,
                new String[]{"https://second.example/water"});

        JSONArray observations = observations();
        assertEquals(2, observations.length());
        JSONObject a = observations.getJSONObject(0);
        JSONObject b = observations.getJSONObject(1);
        assertNotEquals(a.getString("id"), b.getString("id"));
        assertEquals(a.getString("clusterId"), b.getString("clusterId"));
        assertEquals("POSSIBLE_CONTRADICTION_OR_CONDITION", b.getString("relationToCluster"));
        assertTrue(b.getString("preliminaryConclusion").contains("Возможно противоречие"));
        assertEquals(a.getString("id"), b.getString("basisObservationId"));
    }

    @Test
    public void zeroUsableFindingsDoNotCompleteTheResearchTopic() throws Exception {
        String report = "БЕСПЛАТНОЕ ВЕБ-ИССЛЕДОВАНИЕ\n"
                + "1. A page without URL\n"
                + "Фрагменты страницы:\n"
                + "• Water boils at 100 degrees Celsius at sea-level atmospheric pressure.\n";
        int count = InitiativeResearchAgenda.saveResult(context, "continuity", "Преемственность",
                "Find ways to preserve context", report,
                new String[]{"https://first.example/page", "https://second.example/page"});

        assertEquals(0, count);
        assertEquals(0, observations().length());
        InitiativeResearchAgenda.Topic topic = InitiativeResearchAgenda.get(context, "continuity");
        assertNotNull(topic);
        assertFalse(topic.completed);
        assertTrue(InitiativeResearchAgenda.latestResultText(context).contains("незаверш"));
    }

    @Test
    public void oppositeNegationIsFlaggedForReview() throws Exception {
        String first = oneSourceReport("https://first.example/system",
                "The system supports local clustering for research observations.");
        String second = oneSourceReport("https://second.example/system",
                "The system does not support local clustering for research observations.");
        ResearchKnowledgeStore.ingestReport(context, "Local clustering", "Question", first,
                new String[]{"https://first.example/system"});
        ResearchKnowledgeStore.ingestReport(context, "Local clustering", "Question", second,
                new String[]{"https://second.example/system"});

        JSONArray observations = observations();
        assertEquals(2, observations.length());
        assertEquals("POSSIBLE_CONTRADICTION_OR_CONDITION",
                observations.getJSONObject(1).getString("relationToCluster"));
        assertEquals(observations.getJSONObject(0).getString("id"),
                observations.getJSONObject(1).getString("basisObservationId"));
    }

    @Test
    public void saveResultAddsOneDerivedTakeawayButNeverUsesItAsIndependentEvidence() throws Exception {
        String url = "https://first.example/water";
        String report = oneSourceReport(url,
                "Water boils at 100 degrees Celsius at sea-level atmospheric pressure.");

        assertEquals(1, InitiativeResearchAgenda.saveResult(
                context, "", "Точка кипения", "Исследуй точку кипения воды", report,
                new String[]{url}));
        // Re-delivery of the same report updates repeat metadata rather than making new claims.
        assertEquals(1, InitiativeResearchAgenda.saveResult(
                context, "", "Точка кипения", "Исследуй точку кипения воды", report,
                new String[]{url}));

        JSONArray observations = observations();
        assertEquals(2, observations.length());
        JSONObject raw = observations.getJSONObject(0);
        JSONObject takeaway = observations.getJSONObject(1);
        assertEquals("FREE_WEB_EXTRACT", raw.getString("origin"));
        assertEquals("AGENT_PRELIMINARY_CONCLUSION", takeaway.getString("origin"));
        assertEquals(raw.getString("clusterId"), takeaway.getString("clusterId"));
        assertEquals(raw.getString("id"), takeaway.getString("basisObservationId"));
        assertTrue(takeaway.getString("preliminaryConclusion").contains("не является независимым подтверждением"));
        assertTrue(ResearchKnowledgeStore.summaryText(context).contains("Предварительный вывод"));
        assertEquals(2, raw.getInt("repeatCount"));
        assertEquals(2, takeaway.getInt("repeatCount"));
    }

    @Test
    public void takeawayUsesQuotedSnippetSourceEvenWhenCallerUrlArrayIsReversed() throws Exception {
        String firstUrl = "https://first.example/water";
        String secondUrl = "https://second.example/boiling";
        String report = "БЕСПЛАТНОЕ ВЕБ-ИССЛЕДОВАНИЕ\n"
                + "Вопрос: точка кипения воды\n"
                + "Метод: extractive\n\n"
                + "1. Первый источник\n"
                + "Домен: first.example\n"
                + "URL: " + firstUrl + "\n"
                + "Фрагменты страницы:\n"
                + "• Water boils at 100 degrees Celsius at sea-level atmospheric pressure.\n\n"
                + "2. Второй источник\n"
                + "Домен: second.example\n"
                + "URL: " + secondUrl + "\n"
                + "Фрагменты страницы:\n"
                + "• Water reaches boiling at 100 degrees Celsius near sea-level atmospheric pressure.\n\n"
                + "Что пока НЕ установлено автоматически:\n"
                + "• The sources have not been independently verified.\n"
                + "Следующий шаг: inspect the original sources.";

        assertEquals(2, InitiativeResearchAgenda.saveResult(context, "", "Точка кипения",
                "Сравни источники о точке кипения воды", report,
                new String[]{secondUrl, firstUrl}));

        JSONArray rows = observations();
        JSONObject raw = rows.getJSONObject(0);
        JSONObject takeaway = null;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            if ("AGENT_PRELIMINARY_CONCLUSION".equals(row.optString("origin"))) {
                takeaway = row;
                break;
            }
        }
        assertNotNull(takeaway);
        assertEquals("The quote comes from the first report block, not urls[] order.",
                firstUrl, takeaway.getString("sourceUrl"));
        assertEquals(raw.getString("id"), takeaway.getString("basisObservationId"));
        JSONArray evidenceUrls = takeaway.getJSONArray("evidenceUrls");
        assertEquals(firstUrl, evidenceUrls.getString(0));
        assertEquals(2, evidenceUrls.length());
    }

    @Test
    public void derivedTakeawayNeverBecomesTheBasisForAnotherTakeaway() throws Exception {
        String url = "https://first.example/water";
        String report = oneSourceReport(url,
                "Water boils at 100 degrees Celsius at sea-level atmospheric pressure.");
        InitiativeResearchAgenda.saveResult(context, "", "Boiling point", "First question", report,
                new String[]{url});
        InitiativeResearchAgenda.saveResult(context, "", "Water phase change", "Second question", report,
                new String[]{url});

        JSONArray rows = observations();
        assertEquals(3, rows.length());
        String rawId = rows.getJSONObject(0).getString("id");
        JSONObject firstTakeaway = null;
        JSONObject secondTakeaway = null;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            if (!"AGENT_PRELIMINARY_CONCLUSION".equals(row.optString("origin"))) continue;
            if (firstTakeaway == null) firstTakeaway = row;
            else secondTakeaway = row;
        }
        assertNotNull(firstTakeaway);
        assertNotNull(secondTakeaway);
        assertEquals(rawId, firstTakeaway.getString("basisObservationId"));
        assertEquals(rawId, secondTakeaway.getString("basisObservationId"));
        assertNotEquals(firstTakeaway.getString("id"), secondTakeaway.getString("basisObservationId"));
        assertEquals("DERIVED_FROM_OBSERVATION", secondTakeaway.getString("relationToCluster"));
    }

    @Test
    public void localHypothesisIsUpsertedByStableIdWithoutInventingEvidenceUrls() throws Exception {
        String hypothesisId = "h-pressure-rule";
        String rule = "Water boiling temperature depends on surrounding pressure conditions.";
        assertEquals(1, ResearchKnowledgeStore.upsertLocalHypothesis(context, hypothesisId,
                rule, "Observed local pairs vary pressure and boiling point.", "CANDIDATE", 2, "1"));

        JSONArray firstRows = observations();
        assertEquals(1, firstRows.length());
        JSONObject first = firstRows.getJSONObject(0);
        String observationId = first.getString("id");
        assertEquals("LOCAL_SYMBOLIC_HYPOTHESIS", first.getString("origin"));
        assertEquals("local-hypothesis:" + hypothesisId, first.getString("basisHypothesisId"));
        assertEquals("", first.getString("sourceUrl"));
        assertEquals(0, first.getJSONArray("evidenceUrls").length());
        assertEquals(1, first.getInt("repeatCount"));

        assertEquals(1, ResearchKnowledgeStore.upsertLocalHypothesis(context, hypothesisId,
                rule, "Additional local comparison; no web URL attached.", "REJECTED", 3, "2"));

        JSONArray rows = observations();
        assertEquals("A repeated scan must update the same hypothesis record.", 1, rows.length());
        JSONObject updated = rows.getJSONObject(0);
        assertEquals(observationId, updated.getString("id"));
        assertEquals("REJECTED", updated.getString("hypothesisStatus"));
        assertEquals(3, updated.getInt("hypothesisSupport"));
        assertEquals("2", updated.getString("hypothesisVersion"));
        assertEquals(0.05, updated.getDouble("confidence"), 0.0001);
        assertEquals("A scan is not new evidence.", 1, updated.getInt("repeatCount"));
        assertTrue(updated.getString("basis").contains("no web URL attached"));
    }

    @Test
    public void localHypothesisJoinsRelatedSourceClusterWithoutBecomingSourceEvidence() throws Exception {
        String url = "https://first.example/water";
        String report = oneSourceReport(url,
                "Water boils at 100 degrees Celsius at sea-level atmospheric pressure.");
        assertEquals(1, ResearchKnowledgeStore.ingestReport(context, "Boiling", "Water pressure", report,
                new String[]{url}));
        JSONObject raw = observations().getJSONObject(0);

        assertEquals(1, ResearchKnowledgeStore.upsertLocalHypothesis(context, "h-boiling-pressure",
                "Water boils at 100 degrees Celsius under sea-level pressure conditions.",
                "The local symbolic pattern compares pressure and boiling point.", "CANDIDATE", 2, "1"));

        JSONArray rows = observations();
        assertEquals(2, rows.length());
        JSONObject hypothesis = rows.getJSONObject(1);
        assertEquals(raw.getString("clusterId"), hypothesis.getString("clusterId"));
        assertEquals("SIMILAR_TO_SOURCE_OBSERVATION", hypothesis.getString("relationToCluster"));
        assertEquals(raw.getString("id"), hypothesis.getString("similarToObservationId"));
        assertEquals("", hypothesis.getString("basisObservationId"));
        assertEquals("", hypothesis.getString("sourceUrl"));
        assertEquals(0, hypothesis.getJSONArray("evidenceUrls").length());
    }

    @Test
    public void localSymbolicHypothesisUpsertIsIdempotentAndNeverInventsSourceUrls() throws Exception {
        assertEquals(1, ResearchKnowledgeStore.upsertLocalHypothesis(context,
                "h-local-7", "Повторяющийся переход A к B при условии C",
                "Наблюдалось в двух локальных парах", "CANDIDATE", 2, "1"));

        JSONArray firstRows = observations();
        assertEquals(1, firstRows.length());
        JSONObject first = firstRows.getJSONObject(0);
        String id = first.getString("id");
        String clusterId = first.getString("clusterId");
        assertEquals("local-hypothesis:h-local-7", first.getString("basisHypothesisId"));
        assertEquals("LOCAL_SYMBOLIC_HYPOTHESIS", first.getString("origin"));
        assertEquals("", first.getString("sourceUrl"));
        assertEquals(0, first.getJSONArray("evidenceUrls").length());
        assertEquals(1, first.getInt("repeatCount"));
        assertTrue(first.getString("basis").contains("Повторяющийся переход A к B"));
        assertTrue(first.getString("basis").contains("status=CANDIDATE"));

        assertEquals(1, ResearchKnowledgeStore.upsertLocalHypothesis(context,
                "h-local-7", "Уточнённый переход A к B при условии C",
                "Добавлено ещё одно локальное наблюдение", "ACTIVE", 3, "2"));

        JSONArray updatedRows = observations();
        assertEquals("Повторный scan должен обновить запись, а не создать новую.", 1, updatedRows.length());
        JSONObject updated = updatedRows.getJSONObject(0);
        assertEquals(id, updated.getString("id"));
        assertEquals(clusterId, updated.getString("clusterId"));
        assertEquals("local-hypothesis:h-local-7", updated.getString("basisHypothesisId"));
        assertTrue(updated.getString("text").contains("Уточнённый переход A к B"));
        assertEquals("ACTIVE", updated.getString("hypothesisStatus"));
        assertEquals(3, updated.getInt("hypothesisSupport"));
        assertEquals("2", updated.getString("hypothesisVersion"));
        assertEquals(1, updated.getInt("repeatCount"));
        assertEquals("", updated.getString("sourceUrl"));
        assertEquals(0, updated.getJSONArray("evidenceUrls").length());
        assertTrue(ResearchKnowledgeStore.summaryText(context).contains("Локальная гипотеза агента"));
    }

    @Test
    public void initiativeScanUpsertsLocalHypothesisWithoutFakeEvidenceOrRepeatedSupport() throws Exception {
        context.getSharedPreferences("vector_memory", Context.MODE_PRIVATE).edit().clear().commit();
        MemoryStore memory = new MemoryStore(context);
        memory.add("zorbaxis readings are high today", 0.35, false, "TEST", "controlled pattern", "");
        memory.add("zorbaxis readings are low today", 0.35, false, "TEST", "controlled pattern", "");
        memory.add("zorbaxis readings are warm today", 0.35, false, "TEST", "controlled pattern", "");

        MemoryStore.Hypothesis hypothesis = null;
        for (MemoryStore.Hypothesis candidate : memory.hypotheses()) {
            if (candidate.rule.contains("zorbaxis")) { hypothesis = candidate; break; }
        }
        assertNotNull("Repeated one-token differences should produce a local hypothesis.", hypothesis);

        InitiativeEngine.scan(context);
        JSONObject first = findLocalHypothesis(hypothesis.id);
        assertNotNull("Initiative scan should upsert the saved local hypothesis.", first);
        assertEquals("LOCAL_SYMBOLIC_HYPOTHESIS", first.getString("origin"));
        assertEquals("local-hypothesis:" + hypothesis.id, first.getString("basisHypothesisId"));
        assertEquals("", first.getString("sourceUrl"));
        assertEquals(0, first.getJSONArray("evidenceUrls").length());
        assertTrue(first.getString("basis").contains("no external source URL is asserted"));
        String observationId = first.getString("id");
        int observationCount = observations().length();
        int repeatCount = first.getInt("repeatCount");

        memory.learnHypothesis(hypothesis.id, false);
        InitiativeEngine.scan(context);
        JSONObject rejected = findLocalHypothesis(hypothesis.id);
        assertNotNull(rejected);
        assertEquals(observationId, rejected.getString("id"));
        assertEquals("REJECTED", rejected.getString("hypothesisStatus"));
        assertEquals(observationCount, observations().length());
        assertEquals(repeatCount, rejected.getInt("repeatCount"));

        InitiativeEngine.scan(context);
        JSONObject again = findLocalHypothesis(hypothesis.id);
        assertEquals(observationId, again.getString("id"));
        assertEquals(observationCount, observations().length());
        assertEquals(repeatCount, again.getInt("repeatCount"));
    }

    private JSONObject findLocalHypothesis(String hypothesisId) throws Exception {
        JSONArray all = observations();
        String stableId = "local-hypothesis:" + hypothesisId;
        for (int i = all.length() - 1; i >= 0; i--) {
            JSONObject item = all.getJSONObject(i);
            if ("LOCAL_SYMBOLIC_HYPOTHESIS".equals(item.optString("origin"))
                    && stableId.equals(item.optString("basisHypothesisId"))) return item;
        }
        return null;
    }

    @Test
    public void localHypothesisJoinsSourceClusterWhenEvidenceArrivesLater() throws Exception {
        String hypothesisId = "late-source-link";
        String statement = "The Android scheduler may delay background work during battery restrictions on this device.";
        assertEquals(1, ResearchKnowledgeStore.upsertLocalHypothesis(
                context, hypothesisId, statement, "local symbolic pattern", "CANDIDATE", 1, "1"));

        JSONObject before = findLocalHypothesis(hypothesisId);
        assertNotNull(before);
        String originalHypothesisId = before.getString("id");
        String localOnlyClusterId = before.getString("clusterId");

        String url = "https://developer.android.com/guide/background";
        String report = oneSourceReport(url, statement);
        assertEquals(1, ResearchKnowledgeStore.ingestReport(
                context, "Android background work", "Research background scheduling", report,
                new String[]{url}));

        JSONObject raw = null;
        JSONArray afterSource = observations();
        for (int i = 0; i < afterSource.length(); i++) {
            JSONObject row = afterSource.getJSONObject(i);
            if ("FREE_WEB_EXTRACT".equals(row.optString("origin"))) { raw = row; break; }
        }
        assertNotNull(raw);
        assertNotEquals(localOnlyClusterId, raw.getString("clusterId"));

        int recordsBeforeUpsert = observations().length();
        assertEquals(1, ResearchKnowledgeStore.upsertLocalHypothesis(
                context, hypothesisId, statement, "local symbolic pattern", "ACTIVE", 2, "2"));

        JSONObject updated = findLocalHypothesis(hypothesisId);
        assertNotNull(updated);
        assertEquals(originalHypothesisId, updated.getString("id"));
        assertEquals(raw.getString("clusterId"), updated.getString("clusterId"));
        assertEquals("SIMILAR_TO_SOURCE_OBSERVATION", updated.getString("relationToCluster"));
        assertEquals(raw.getString("id"), updated.getString("similarToObservationId"));
        assertEquals("ACTIVE", updated.getString("hypothesisStatus"));
        assertEquals("", updated.getString("sourceUrl"));
        assertEquals(0, updated.getJSONArray("evidenceUrls").length());
        assertEquals(recordsBeforeUpsert, observations().length());
    }

    private String oneSourceReport(String url, String statement) {
        String host = url.replaceFirst("^https?://", "").split("/")[0];
        return "БЕСПЛАТНОЕ ВЕБ-ИССЛЕДОВАНИЕ\n"
                + "Вопрос: точка кипения воды\n"
                + "Метод: extractive\n\n"
                + "1. Источник\n"
                + "Домен: " + host + "\n"
                + "URL: " + url + "\n"
                + "Фрагменты страницы:\n"
                + "• " + statement + "\n\n"
                + "Что пока НЕ установлено автоматически:\n"
                + "• Source independence is not established.\n"
                + "Следующий шаг: check the original source.";
    }

    private SharedPreferences preferences() {
        return context.getSharedPreferences("vector_research_knowledge", Context.MODE_PRIVATE);
    }

    private JSONArray observations() throws Exception {
        return new JSONArray(preferences().getString("observations", "[]"));
    }
}
