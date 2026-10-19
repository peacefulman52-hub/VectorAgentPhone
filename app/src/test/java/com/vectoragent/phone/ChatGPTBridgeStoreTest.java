package com.vectoragent.phone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
public class ChatGPTBridgeStoreTest {
    private Context context;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("entropia_chatgpt_bridge", Context.MODE_PRIVATE)
                .edit().clear().commit();
        deleteRecursively(new File(context.getFilesDir(), "chatgpt-bridge"));
        deleteRecursively(new File(context.getFilesDir(), "mentor-memory"));
        context.getSharedPreferences("vector_research_knowledge", Context.MODE_PRIVATE)
                .edit().clear().commit();
        context.getSharedPreferences("vector_initiative_agenda", Context.MODE_PRIVATE)
                .edit().clear().commit();
        ChatGPTBridgeStore.setMonitoringEnabled(context, false);
    }

    @Test public void monitoringIsOptInAndDisabledSessionDoesNotCaptureText() {
        ChatGPTBridgeStore.observeVisibleTexts(context, "com.openai.chatgpt",
                Collections.singletonList("A private prompt that must not be saved yet."));
        assertTrue(ChatGPTBridgeStore.getLatestSnapshot(context).isEmpty());
        assertFalse(ChatGPTBridgeStore.isMonitoringEnabled(context));
    }

    @Test public void codewordReplyIsImportedAsPreliminaryAndNeverAsFact() {
        MemoryStore memory = new MemoryStore(context);
        int initialFacts = memory.all().size();
        ChatGPTBridgeStore.setMonitoringEnabled(context, true);

        String prompt = "пиши Энтропии — сохрани следующий ответ в память приложения";
        ChatGPTBridgeStore.observeVisibleTexts(context, "com.openai.chatgpt",
                Arrays.asList("ChatGPT", prompt, "Send"));
        assertTrue(ChatGPTBridgeStore.getLatestSnapshot(context).contains(prompt));
        assertFalse(ChatGPTBridgeStore.hasPendingReply(context));

        String answer = "Проверка связи успешна. Этот ответ сохранён локально как предварительный текст, а не как проверенный факт.";
        ChatGPTBridgeStore.observeVisibleTexts(context, "com.openai.chatgpt",
                Arrays.asList("ChatGPT", prompt, answer, "Copy", "Share"));

        assertTrue(ChatGPTBridgeStore.getLatestReply(context).contains(answer));
        assertTrue(ChatGPTBridgeStore.hasPendingReply(context));
        ChatGPTBridgeStore.finishSession(context);

        assertTrue(MentorExchangeStore.latestReply(context).contains(answer));
        assertTrue(ChatGPTBridgeStore.getLatestImportedReply(context).contains(answer));
        assertFalse(read(MentorExchangeStore.file(context, "VERIFIED_FACTS.md")).contains(answer));
        assertFalse(ResearchKnowledgeStore.summaryText(context).contains(answer));
        assertEquals("Captured text must not change factual MemoryStore records",
                initialFacts, new MemoryStore(context).all().size());
        assertTrue(ChatGPTBridgeStore.archiveCount(context) > 0);
    }

    @Test public void browserCaptureIsRestrictedToChatGPTDomains() {
        assertTrue(ChatGPTBridgeStore.isChatGPTBrowserPage(Collections.singletonList("https://chatgpt.com/")));
        assertTrue(ChatGPTBridgeStore.isChatGPTBrowserPage(Collections.singletonList("https://chat.openai.com/")));
        assertFalse(ChatGPTBridgeStore.isChatGPTBrowserPage(Collections.singletonList("https://example.org/")));
        assertTrue(ChatGPTBridgeStore.isChatGPTPackage("com.openai.chatgpt"));
        assertFalse(ChatGPTBridgeStore.isChatGPTPackage("com.android.chrome"));
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
