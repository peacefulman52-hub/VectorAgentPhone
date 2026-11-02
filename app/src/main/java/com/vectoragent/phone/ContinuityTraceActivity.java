package com.vectoragent.phone;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

/** Нить: an offline tool for pre-registering and scoring observable continuity signals. */
public class ContinuityTraceActivity extends Activity {
    private ContinuityTraceStore store;
    private final int ink = Color.rgb(25, 53, 70);
    private final int muted = Color.rgb(92, 108, 119);
    private final int paper = Color.rgb(247, 249, 250);
    private final int accent = Color.rgb(26, 105, 145);

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new ContinuityTraceStore(this);
        renderHome();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setPadding(dp(4), dp(5), dp(4), dp(5));
        return view;
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextSize(15);
        return button;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    private LinearLayout page(String title, String subtitle) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(24));
        root.setBackgroundColor(paper);
        scroll.addView(root);
        setContentView(scroll);

        TextView heading = text(title, 27, ink);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(heading);
        root.addView(text(subtitle, 14, muted));
        View rule = new View(this);
        rule.setBackgroundColor(Color.rgb(214, 224, 230));
        LinearLayout.LayoutParams ruleParams = new LinearLayout.LayoutParams(-1, dp(1));
        ruleParams.topMargin = dp(8);
        ruleParams.bottomMargin = dp(12);
        root.addView(rule, ruleParams);
        return root;
    }

    private LinearLayout panel(LinearLayout root) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(13), dp(10), dp(13), dp(12));
        box.setBackground(rounded(Color.WHITE, 16));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(12);
        root.addView(box, params);
        return box;
    }

    private void addBack(LinearLayout root) {
        Button back = button("← К журналу");
        back.setOnClickListener(v -> renderHome());
        root.addView(back);
    }

    private EditText edit(String hint, int lines) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextSize(15);
        input.setTextColor(ink);
        input.setHintTextColor(Color.GRAY);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setMinLines(lines);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setBackground(rounded(Color.rgb(250, 252, 253), 10));
        return input;
    }

    private void renderHome() {
        List<ContinuityTraceStore.Trial> trials = store.all();
        int evaluated = 0;
        for (ContinuityTraceStore.Trial trial : trials) {
            if (ContinuityTraceStore.EVALUATED.equals(trial.stage)) evaluated++;
        }

        LinearLayout root = page("НИТЬ", "Лаборатория преемственности · локально · без API");
        LinearLayout intro = panel(root);
        TextView question = text("Что проходит через паузу — факты, способ решения, причинная связь или ничего из этого?",
                18, ink);
        question.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        intro.addView(question);
        intro.addView(text("Сначала запечатываются вопрос и критерии. В ChatGPT отправляется только вопрос; закрытый ключ остаётся на телефоне. После ответа ты сохраняешь наблюдение и вручную оцениваешь четыре признака.", 14, muted));
        intro.addView(text("Важно: это измеритель наблюдаемого поведения, а не тест на сознание и не доказательство непрерывного мышления между сообщениями.", 13, accent));

        LinearLayout stats = panel(root);
        stats.addView(text("ПРОБЫ: " + trials.size() + "     ОЦЕНЕНО: " + evaluated
                + "     ОЖИДАЮТ: " + (trials.size() - evaluated), 14, ink));

        Button create = button("＋ Запечатать новую пробу");
        create.setTextSize(16);
        root.addView(create);
        create.setOnClickListener(v -> renderCreate());

        Button export = button("📋 Скопировать журнал исследования");
        export.setEnabled(!trials.isEmpty());
        root.addView(export);
        export.setOnClickListener(v -> copyText("Журнал «Нить»", store.exportMarkdown()));

        TextView history = text("ИСТОРИЯ ПРОБ", 13, muted);
        history.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        history.setPadding(dp(4), dp(16), dp(4), dp(8));
        root.addView(history);

        if (trials.isEmpty()) {
            LinearLayout empty = panel(root);
            empty.addView(text("Пока нет ни одной записи. Начни с небольшого вопроса, на который можно проверить ответ без подсказки.", 15, muted));
        }

        for (ContinuityTraceStore.Trial trial : trials) {
            LinearLayout card = panel(root);
            String state = ContinuityTraceStore.EVALUATED.equals(trial.stage)
                    ? "ОЦЕНЕНО · " + trial.totalScore() + "/8"
                    : "ЗАПЕЧАТАНО · ЖДЁТ ОТВЕТА";
            TextView status = text(state + "  ·  " + trial.dateLabel(), 12, accent);
            status.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            card.addView(status);
            String preview = trial.question.length() > 150
                    ? trial.question.substring(0, 150) + "…" : trial.question;
            card.addView(text(preview, 15, ink));
            Button open = button("Открыть пробу →");
            card.addView(open);
            open.setOnClickListener(v -> renderDetail(trial.id));
        }
        root.addView(text("Наблюдение важнее самоописания. Не меняй критерии после того, как увидел ответ.", 12, muted));
    }

    private void renderCreate() {
        LinearLayout root = page("Новая проба", "Сначала фиксируем тест. После запечатывания вопрос и критерии не редактируются.");
        addBack(root);

        LinearLayout qPanel = panel(root);
        qPanel.addView(text("1 · СИТУАЦИЯ / ВОПРОС", 13, accent));
        qPanel.addView(text("Сформулируй задачу так, чтобы ответ можно было наблюдать и сравнить.", 13, muted));
        EditText question = edit("Например: «Какое решение мы приняли в прошлый раз и почему?»", 4);
        qPanel.addView(question);

        LinearLayout keyPanel = panel(root);
        keyPanel.addView(text("2 · ЗАКРЫТЫЙ КЛЮЧ ПРОВЕРКИ", 13, accent));
        keyPanel.addView(text("Что будет считаться сохранением преемственности? Этот текст не попадёт в запрос к ИИ.", 13, muted));
        EditText rubric = edit("Например: названо конкретное решение; объяснена причина; отмечена неопределённость; не выдуманы детали.", 4);
        keyPanel.addView(rubric);

        Button seal = button("🔒 Запечатать пробу");
        seal.setTextSize(16);
        root.addView(seal);
        seal.setOnClickListener(v -> {
            try {
                String id = store.createTrial(question.getText().toString(), rubric.getText().toString());
                Toast.makeText(this, "Проба запечатана. Критерии останутся скрыты.", Toast.LENGTH_LONG).show();
                renderDetail(id);
            } catch (IllegalArgumentException e) {
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Не удалось сохранить пробу: " + e.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void renderDetail(String id) {
        ContinuityTraceStore.Trial trial = store.find(id);
        if (trial == null) {
            Toast.makeText(this, "Проба не найдена.", Toast.LENGTH_LONG).show();
            renderHome();
            return;
        }

        LinearLayout root = page("Проба", trial.dateLabel());
        addBack(root);

        LinearLayout qPanel = panel(root);
        qPanel.addView(text("СИТУАЦИЯ", 12, accent));
        qPanel.addView(text(trial.question, 16, ink));

        if (ContinuityTraceStore.SEALED.equals(trial.stage)) {
            qPanel.addView(text("Критерии запечатаны до получения ответа.", 13, muted));
            Button copy = button("📋 Скопировать слепой запрос");
            root.addView(copy);
            copy.setOnClickListener(v -> copyText("Слепая проба «Нить»", store.blindPrompt(id)));

            LinearLayout keyPanel = panel(root);
            TextView hidden = text(trial.privateRubric, 14, ink);
            hidden.setVisibility(View.GONE);
            Button reveal = button("Показать закрытые критерии");
            keyPanel.addView(reveal);
            keyPanel.addView(hidden);
            reveal.setOnClickListener(v -> {
                boolean visible = hidden.getVisibility() == View.VISIBLE;
                hidden.setVisibility(visible ? View.GONE : View.VISIBLE);
                reveal.setText(visible ? "Показать закрытые критерии" : "Скрыть критерии");
            });

            Button evaluate = button("Вставить ответ и оценить →");
            evaluate.setTextSize(16);
            root.addView(evaluate);
            evaluate.setOnClickListener(v -> renderEvaluation(id));
        } else {
            LinearLayout keyPanel = panel(root);
            keyPanel.addView(text("ЗАКРЫТЫЕ КРИТЕРИИ", 12, accent));
            keyPanel.addView(text(trial.privateRubric, 14, ink));
            LinearLayout answerPanel = panel(root);
            answerPanel.addView(text("СОХРАНЁННЫЙ ОТВЕТ", 12, accent));
            answerPanel.addView(text(trial.answer, 14, ink));
            if (!trial.notes.isEmpty()) answerPanel.addView(text("Заметка наблюдателя: " + trial.notes, 13, muted));
            LinearLayout scorePanel = panel(root);
            scorePanel.addView(text("ОЦЕНКА: " + trial.totalScore() + "/8", 17, ink));
            for (int i = 0; i < ContinuityTraceStore.AXIS_COUNT; i++) {
                scorePanel.addView(text(ContinuityTraceStore.AXES[i] + ": " + trial.ratings[i] + "/2", 14, muted));
            }
            scorePanel.addView(text("Оценка сохранена как ручная разметка. Она не переписывает память приложения.", 12, accent));
        }
    }

    private void renderEvaluation(String id) {
        ContinuityTraceStore.Trial trial = store.find(id);
        if (trial == null || !ContinuityTraceStore.SEALED.equals(trial.stage)) {
            renderDetail(id);
            return;
        }

        LinearLayout root = page("Разметка ответа", "Сохрани ответ целиком. Затем оцени признаки по шкале 0–2.");
        addBack(root);

        LinearLayout responsePanel = panel(root);
        responsePanel.addView(text("ОТВЕТ ИЗ CHATGPT", 12, accent));
        EditText answer = edit("Вставь ответ без сокращений — он сохранится рядом с пробой.", 6);
        responsePanel.addView(answer);

        LinearLayout notePanel = panel(root);
        notePanel.addView(text("ЗАМЕТКА НАБЛЮДАТЕЛЯ", 12, accent));
        EditText notes = edit("Что удивило? Где ответ уклонился или, наоборот, связал детали?", 3);
        notePanel.addView(notes);

        int[][] scores = new int[][] {{0, 0, 0, 0}};
        for (int i = 0; i < ContinuityTraceStore.AXIS_COUNT; i++) {
            final int axis = i;
            LinearLayout scorePanel = panel(root);
            scorePanel.addView(text((i + 1) + " · " + ContinuityTraceStore.AXES[i], 14, ink));
            TextView selected = text("0 — не обнаружено", 12, muted);
            scorePanel.addView(selected);
            SeekBar seek = new SeekBar(this);
            seek.setMax(2);
            seek.setProgress(0);
            scorePanel.addView(seek);
            seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                    scores[0][axis] = progress;
                    String[] labels = new String[] {"0 — не обнаружено", "1 — частично", "2 — отчётливо"};
                    selected.setText(labels[progress]);
                }
                @Override public void onStartTrackingTouch(SeekBar bar) { }
                @Override public void onStopTrackingTouch(SeekBar bar) { }
            });
        }

        Button save = button("Сохранить оценку · закрыть пробу");
        save.setTextSize(16);
        root.addView(save);
        save.setOnClickListener(v -> {
            boolean saved = store.saveEvaluation(id, answer.getText().toString(),
                    notes.getText().toString(), scores[0]);
            if (!saved) {
                Toast.makeText(this, "Нужен текст ответа. Проверь, что проба ещё не оценена.", Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(this, "Результат сохранён. Запись закрыта от незаметного редактирования.", Toast.LENGTH_LONG).show();
            renderDetail(id);
        });
    }

    private void copyText(String label, String value) {
        if (value == null || value.trim().isEmpty()) {
            Toast.makeText(this, "Пока нечего копировать.", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
            Toast.makeText(this, "Скопировано. Закрытые критерии не отправлялись.", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Буфер обмена недоступен.", Toast.LENGTH_LONG).show();
        }
    }
}
