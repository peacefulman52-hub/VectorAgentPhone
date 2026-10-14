package com.vectoragent.phone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;

@RunWith(RobolectricTestRunner.class)
public class MentorExchangeStoreTest {
    private Context context;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        deleteRecursively(new File(context.getFilesDir(), "mentor-memory"));
        context.getSharedPreferences("vector_initiative_agenda", Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences("vector_research_knowledge", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test public void handoffIncludesLatestResearchAndSafetyRules() {
        InitiativeResearchAgenda.saveResult(
                context,
                "",
                "Тест преемственности",
                "Как переносить контекст между сессиями?",
                "Источник 1: Краткий тестовый текст о контрольных точках и журнале событий.\nhttps://example.org/continuity",
                new String[]{"https://example.org/continuity"});

        String prompt = MentorExchangeStore.buildMentorPrompt(context);
        assertTrue(prompt.contains("Тест преемственности"));
        assertTrue(prompt.contains("Не превращай свой ответ в факт"));
        assertTrue(prompt.contains("VERIFIED_FACTS"));
        assertTrue(MentorExchangeStore.file(context, "CHECKPOINT.md").isFile());
        assertTrue(MentorExchangeStore.file(context, "EVENTS.jsonl").isFile());
    }

    @Test public void mentorReplyIsLoggedButNotPromotedToVerifiedFactsOrMemory() {
        MemoryStore memory = new MemoryStore(context);
        int initialFacts = memory.all().size();
        String reply = "Наставник рекомендует сравнить три режима handoff на одинаковых задачах, фиксируя потери решений, повторы ошибок и время восстановления.";

        String status = MentorExchangeStore.importMentorReply(context, reply, false);
        assertTrue(status.startsWith("Обработка завершена"));
        assertTrue(status.contains("Отчёт: сохранён"));
        assertTrue(status.contains("В обычные знания не добавлено"));
        assertTrue(MentorExchangeStore.latestReply(context).contains(reply));
        assertTrue(read(MentorExchangeStore.file(context, "LATEST_MENTOR_ANALYSIS.md")).contains("Отчёт локальной обработки"));
        assertTrue(read(MentorExchangeStore.file(context, "EVENTS.jsonl")).contains("MENTOR_REPLY_IMPORTED"));
        assertFalse(read(MentorExchangeStore.file(context, "VERIFIED_FACTS.md")).contains(reply));
        assertFalse(read(MentorExchangeStore.file(context, "DECISIONS.md")).contains(reply));
        assertEquals(initialFacts, new MemoryStore(context).all().size());
    }

    @Test public void decisionLogChangesOnlyAfterExplicitUserConfirmation() {
        String reply = "Проводить следующий эксперимент только после фиксации измеримых критериев успеха.";
        MentorExchangeStore.importMentorReply(context, reply, true);
        assertTrue(read(MentorExchangeStore.file(context, "DECISIONS.md")).contains("подтверждено пользователем"));
        assertTrue(read(MentorExchangeStore.file(context, "DECISIONS.md")).contains(reply));
    }

    private static String read(File file) {
        try {
            return new String(java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) { return ""; }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteRecursively(child);
        file.delete();
    }
}
