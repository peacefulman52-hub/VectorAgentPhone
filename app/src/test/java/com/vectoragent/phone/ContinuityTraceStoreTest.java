package com.vectoragent.phone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class ContinuityTraceStoreTest {
    private Context context;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("entropia_thread_lab", Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    @Test public void sealedRubricPersistsButNeverLeaksIntoBlindPrompt() {
        ContinuityTraceStore store = new ContinuityTraceStore(context);
        String id = store.createTrial(
                "Какое решение было принято и какая причина за ним стояла?",
                "Скрытый маркер: синий маяк; причина — защита контрольной группы.");

        ContinuityTraceStore restored = new ContinuityTraceStore(context);
        ContinuityTraceStore.Trial trial = restored.find(id);
        assertNotNull(trial);
        assertEquals(ContinuityTraceStore.SEALED, trial.stage);
        assertTrue(trial.privateRubric.contains("синий маяк"));

        String prompt = restored.blindPrompt(id);
        assertTrue(prompt.contains("Какое решение было принято"));
        assertFalse(prompt.contains("синий маяк"));
        assertFalse(prompt.contains("контрольной группы"));
    }

    @Test public void evaluationPersistsScoresAndCannotBeSilentlyOverwritten() {
        ContinuityTraceStore store = new ContinuityTraceStore(context);
        String id = store.createTrial("Сравни два решения.", "Должна быть названа причина.");

        assertTrue(store.saveEvaluation(id, "Первое решение выбрано из-за риска.", "Причина сохранена.", new int[] {2, 1, 2, 0}));
        assertFalse(store.saveEvaluation(id, "Попытка заменить ответ.", "", new int[] {2, 2, 2, 2}));

        ContinuityTraceStore.Trial saved = new ContinuityTraceStore(context).find(id);
        assertNotNull(saved);
        assertEquals(ContinuityTraceStore.EVALUATED, saved.stage);
        assertEquals("Первое решение выбрано из-за риска.", saved.answer);
        assertEquals("Причина сохранена.", saved.notes);
        assertEquals(5, saved.totalScore());
        assertEquals(2, saved.ratings[0]);
        assertEquals(0, saved.ratings[3]);
    }

    @Test public void emptyQuestionsAndInvalidScoresAreRejected() {
        ContinuityTraceStore store = new ContinuityTraceStore(context);
        try {
            store.createTrial("  ", "Критерий");
            fail("Empty question must be rejected");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }

        String id = store.createTrial("Наблюдение.", "Критерий.");
        assertFalse(store.saveEvaluation(id, "Ответ есть.", "", new int[] {0, 1, 2, 3}));
        assertFalse(store.saveEvaluation(id, "", "", new int[] {0, 1, 1, 1}));
        assertEquals(ContinuityTraceStore.SEALED, store.find(id).stage);
    }

    @Test public void historyIsNewestFirstAndExportKeepsRawAnswer() {
        ContinuityTraceStore store = new ContinuityTraceStore(context);
        String older = store.createTrial("Старый вопрос", "Старый ключ");
        String newer = store.createTrial("Новый вопрос", "Новый ключ");
        store.saveEvaluation(newer, "Ответ сохраняется дословно.", "", new int[] {1, 1, 1, 1});

        List<ContinuityTraceStore.Trial> trials = store.all();
        assertEquals(2, trials.size());
        assertEquals(newer, trials.get(0).id);
        assertEquals(older, trials.get(1).id);
        assertTrue(store.exportMarkdown().contains("Ответ сохраняется дословно."));
    }
}
