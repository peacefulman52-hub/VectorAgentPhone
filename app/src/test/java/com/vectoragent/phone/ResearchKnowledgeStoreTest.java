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
    public void refusesToGuessAttributionWhenReportContainsSeveralUrlsButNoBlockUrl() {
        String report = "1. Page without source URL\n"
                + "Фрагменты страницы:\n"
                + "• Water boils at 100 degrees Celsius at sea-level atmospheric pressure.\n";
        int count = ResearchKnowledgeStore.ingestReport(context, "Тема", "Вопрос", report,
                new String[]{"https://first.example/page", "https://second.example/page"});
        assertEquals(0, count);
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
        String report = "БЕСПЛАТНОЕ ВЕБ-ИССЛЕДОВАНИЕ\\n"
                + "1. A page without URL\\n"
                + "Фрагменты страницы:\\n"
                + "• Water boils at 100 degrees Celsius at sea-level atmospheric pressure.\\n";
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
