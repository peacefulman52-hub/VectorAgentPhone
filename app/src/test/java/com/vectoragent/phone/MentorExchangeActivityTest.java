package com.vectoragent.phone;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.io.File;

@RunWith(RobolectricTestRunner.class)
public class MentorExchangeActivityTest {
    @Before public void cleanMentorFiles() {
        android.content.Context context = org.robolectric.RuntimeEnvironment.getApplication();
        deleteRecursively(new File(context.getFilesDir(), "mentor-memory"));
        context.getSharedPreferences("vector_research_knowledge", android.content.Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    @Test public void visibleProcessButtonImportsReplyAsPreliminaryOnly() {
        MentorExchangeActivity activity = Robolectric.buildActivity(MentorExchangeActivity.class).setup().get();
        EditText input = findView(activity.getWindow().getDecorView(), EditText.class);
        assertNotNull("Reply text field should be visible", input);

        String reply = "Контрольная фраза: MENTOR-7319. Наставник рекомендует проверить восстановление решения после перезапуска приложения. Не добавляй в VERIFIED_FACTS.";
        input.setText(reply);

        Button process = findButton(activity.getWindow().getDecorView(), "Обработать ответ наставника");
        assertNotNull("Explicit process button should be visible", process);
        assertTrue("Button should enable for a sufficiently long reply", process.isEnabled());
        process.performClick();

        assertTrue(MentorExchangeStore.latestReply(activity).contains(reply));
        String analysis = MentorExchangeStore.latestAnalysis(activity);
        assertTrue("Analysis should detect the control marker", analysis.contains("MENTOR-7319"));
        assertTrue("Analysis should disclose its limitations", analysis.contains("НЕ семантическое понимание"));
        assertTrue("The test action must be present in the experiment section",
                sectionContains(analysis, "## Эксперименты/проверки", "Наставник рекомендует проверить восстановление решения"));
        assertTrue("The action should also be retained in recommendations",
                sectionContains(analysis, "## Рекомендации", "Наставник рекомендует проверить восстановление решения"));
        assertTrue("The protective instruction should be retained as a recommendation",
                sectionContains(analysis, "## Рекомендации", "Не добавляй в VERIFIED_FACTS"));
        assertTrue("The control marker's sentence should be classified as a claim",
                sectionContains(analysis, "## Утверждения/наблюдения", "Контрольная фраза: MENTOR-7319"));
        assertTrue("Successful processing should clear the input field", input.getText().toString().isEmpty());
        assertNotNull("The on-screen report should refresh after processing",
                findTextContaining(activity.getWindow().getDecorView(), "# Отчёт локальной обработки ответа наставника"));
        assertFalse(read(MentorExchangeStore.file(activity, "VERIFIED_FACTS.md")).contains(reply));
        assertFalse("Mentor text must not be injected into ordinary research knowledge",
                ResearchKnowledgeStore.summaryText(activity).contains(reply));
    }

    private static boolean sectionContains(String markdown, String heading, String value) {
        int start = markdown.indexOf(heading);
        if (start < 0) return false;
        int bodyStart = markdown.indexOf('\n', start);
        if (bodyStart < 0) return false;
        int next = markdown.indexOf("\n## ", bodyStart + 1);
        String section = next < 0 ? markdown.substring(bodyStart + 1) : markdown.substring(bodyStart + 1, next);
        return section.contains(value);
    }

    private static TextView findTextContaining(View view, String fragment) {
        if (view instanceof TextView && ((TextView) view).getText().toString().contains(fragment)) {
            return (TextView) view;
        }
        if (view instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                TextView found = findTextContaining(((ViewGroup) view).getChildAt(i), fragment);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static <T extends View> T findView(View view, Class<T> type) {
        if (type.isInstance(view)) return type.cast(view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                T found = findView(group.getChildAt(i), type);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static Button findButton(View view, String text) {
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
