package com.vectoragent.phone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Verifies the one-tap clipboard control without network access. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MainActivityCopyAnswerTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;

    @Before
    public void setUp() {
        Context app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("vector_context", Context.MODE_PRIVATE)
                .edit().clear().commit();
        controller = Robolectric.buildActivity(MainActivity.class).setup();
        activity = controller.get();
    }

    @After
    public void tearDown() {
        if (controller != null) controller.pause().stop().destroy();
    }

    @Test
    public void copiesOnlyLatestActualAgentReplyAndPreservesItsText() {
        assertNotNull(activity.copyLastAnswerButton);
        // The initial welcome message is not treated as a real answer.
        assertFalse(activity.copyLastAnswerButton.isEnabled());
        assertEquals(1, countCopyButtons(activity.findViewById(android.R.id.content)));

        activity.addBubble("Ты", "Пользовательский вопрос — не копировать");
        activity.addBubble("Веб-исследователь", "Служебный или исследовательский текст");
        assertFalse("Non-agent messages must not enable the copy button",
                activity.copyLastAnswerButton.isEnabled());

        String firstAnswer = "Ответ агента №1\nВторая строка с пробелом ";
        activity.addBubble("Агент", firstAnswer);
        assertTrue(activity.copyLastAnswerButton.isEnabled());

        // Progress messages must not replace the latest agent answer.
        activity.addBubble("Веб-исследователь", "Более новое служебное сообщение");
        activity.copyLastAnswerButton.performClick();
        assertEquals(firstAnswer, clipboardText());

        // A completed, user-facing web research report is also copyable.
        String completedResearch = "Результат бесплатного веб-исследования:\\nВывод со ссылками";
        activity.addBubble("Веб-исследователь", completedResearch);
        activity.copyLastAnswerButton.performClick();
        assertEquals(completedResearch, clipboardText());

        String secondAnswer = "Ответ агента №2";
        activity.addBubble("Агент", secondAnswer);
        activity.copyLastAnswerButton.performClick();
        assertEquals(secondAnswer, clipboardText());
        assertEquals(1, countCopyButtons(activity.findViewById(android.R.id.content)));
    }

    @Test
    public void brotherGptButtonOpensChatGPTAndEnablesLocalMonitoring() {
        Button open = findButton(activity.findViewById(android.R.id.content), "Написать брату GPT");
        assertNotNull("The main screen should expose the ChatGPT bridge button", open);
        open.performClick();

        assertTrue("Opening ChatGPT explicitly enables local monitoring",
                ChatGPTBridgeStore.isMonitoringEnabled(activity));
        Intent started = Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity();
        assertNotNull("The button must open ChatGPT", started);
        assertEquals(Intent.ACTION_VIEW, started.getAction());
        assertEquals("https://chatgpt.com/", started.getDataString());
    }

    private Button findButton(View view, String text) {
        if (view instanceof Button && ((Button) view).getText().toString().contains(text)) {
            return (Button) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                Button found = findButton(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private String clipboardText() {
        ClipboardManager manager = (ClipboardManager)
                activity.getSystemService(Context.CLIPBOARD_SERVICE);
        assertNotNull(manager);
        ClipData clip = manager.getPrimaryClip();
        assertNotNull(clip);
        return clip.getItemAt(0).getText().toString();
    }

    private int countCopyButtons(View view) {
        int count = 0;
        if (view instanceof Button
                && ((Button) view).getText().toString().contains("Скопировать")) {
            count++;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                count += countCopyButtons(group.getChildAt(i));
            }
        }
        return count;
    }
}
