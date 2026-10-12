package com.vectoragent.phone;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** UI for the seeded research programme and proactive local initiative loop. */
public final class InitiativeActivity extends Activity {
    private static final int REQUEST_NOTIFICATIONS = 73;
    private LinearLayout root;
    private TextView status, reportView, agendaView, researchView;
    private EditText customQuestion;
    private Button scanButton, researchButton;
    private boolean researchRunning;

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
    private TextView tv(String s, int size) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(size); t.setTextColor(Color.rgb(35,35,40));
        t.setPadding(dp(8), dp(8), dp(8), dp(8)); return t;
    }
    private Button btn(String s) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); return b; }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        InitiativeResearchAgenda.ensureSeeded(this);
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(10), dp(12), dp(18));
        scroll.addView(root); setContentView(scroll);

        root.addView(tv("🧭 ASTRA INITIATIVE", 24));
        root.addView(tv("Агент уже получил стартовую программу из 7 реальных исследовательских вопросов. Он ищет веб-источники через OpenAI Responses API, сохраняет отчёты и ссылки, а затем переходит к следующей теме. Он не изменяет факты в памяти автоматически.", 14));
        root.addView(tv("Важно: исследование через API расходует твою API-квоту. Добавь ключ в VECTOR → Настройки. Ручной запуск выполняет один поиск; ежедневный режим запускает до одного исследования за цикл расписания.", 13));

        status = tv("", 13); root.addView(status);
        root.addView(tv("Следующая задача", 18));
        reportView = tv("", 15); reportView.setGravity(Gravity.START); root.addView(reportView);

        scanButton = btn("🔎 Найти следующую задачу");
        researchButton = btn("🔬 Исследовать выбранный вопрос сейчас");
        root.addView(scanButton); root.addView(researchButton);
        scanButton.setOnClickListener(v -> refreshInitiative());
        researchButton.setOnClickListener(v -> runResearch());

        root.addView(tv("Добавить собственный исследовательский вопрос", 18));
        customQuestion = new EditText(this);
        customQuestion.setHint("Например: как проверить, что Astra действительно учится на ошибках?");
        customQuestion.setMinLines(2);
        customQuestion.setGravity(Gravity.TOP);
        root.addView(customQuestion);
        Button addQuestion = btn("＋ Добавить вопрос в очередь");
        root.addView(addQuestion);
        addQuestion.setOnClickListener(v -> {
            String q = customQuestion.getText().toString().trim();
            String id = InitiativeResearchAgenda.addCustom(this, q);
            if (id.isEmpty()) {
                Toast.makeText(this, "Вопрос слишком короткий (нужно хотя бы 8 символов).", Toast.LENGTH_SHORT).show();
                return;
            }
            customQuestion.setText("");
            refreshInitiative();
            Toast.makeText(this, "Вопрос сохранён в исследовательской очереди.", Toast.LENGTH_SHORT).show();
        });

        root.addView(tv("Программа и прогресс", 18));
        agendaView = tv("", 13); root.addView(agendaView);
        root.addView(tv("Последний сохранённый результат", 18));
        researchView = tv("", 14);
        researchView.setAutoLinkMask(Linkify.WEB_URLS);
        researchView.setMovementMethod(LinkMovementMethod.getInstance());
        root.addView(researchView);

        Button enable = btn("▶ Включить ежедневное веб-исследование (расходует API)");
        Button disable = btn("⏸ Выключить ежедневное исследование");
        root.addView(enable); root.addView(disable);
        enable.setOnClickListener(v -> enableSchedule());
        disable.setOnClickListener(v -> {
            InitiativeScheduler.disable(this);
            renderStatus("Ежедневное исследование выключено. Запуск вручную по-прежнему доступен.");
        });

        renderStatus(InitiativeScheduler.isEnabled(this)
                ? "Ежедневный режим включён. Проверь, что API-ключ сохранён."
                : "Ежедневный режим выключен.");
        refreshInitiative();
        refreshAgenda();
    }

    @Override protected void onResume() {
        super.onResume();
        if (status != null) renderStatus(InitiativeScheduler.isEnabled(this)
                ? "Ежедневный режим включён. Android может сдвигать время выполнения."
                : "Ежедневный режим выключен.");
    }

    private void enableSchedule() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        } else {
            InitiativeScheduler.enable(this);
            renderStatus("Ежедневное веб-исследование включено. Один запуск в день может расходовать API-квоту.");
            Toast.makeText(this, "Ежедневное исследование включено", Toast.LENGTH_SHORT).show();
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_NOTIFICATIONS) return;
        // Consent is explicit: the button states that daily web research uses the user's API quota.
        InitiativeScheduler.enable(this);
        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        renderStatus(granted
                ? "Ежедневное исследование включено; уведомления разрешены."
                : "Ежедневное исследование включено, но уведомления запрещены. Фоновый запуск всё равно может расходовать API-квоту.");
    }

    private void runResearch() {
        if (researchRunning) return;
        String apiKey = getSharedPreferences("vector_settings", MODE_PRIVATE).getString("openai_key", "").trim();
        if (apiKey.isEmpty()) {
            renderStatus("Нет API-ключа. Открой VECTOR → Настройки → «Подключение ИИ-учителя», сохрани ключ и вернись сюда.");
            return;
        }

        InitiativeEngine.Report selected = InitiativeEngine.scan(this);
        if (!selected.actionable) {
            render(selected);
            renderStatus("В очереди больше нет незавершённых задач. Добавь новый вопрос выше.");
            return;
        }

        MemoryStore store = new MemoryStore(this);
        String topicId = "";
        String title = selected.title;
        String question = "";

        if ("RESEARCH".equals(selected.taskType)) {
            InitiativeResearchAgenda.Topic topic = InitiativeResearchAgenda.get(this, selected.taskId);
            if (topic != null && !topic.completed) {
                topicId = topic.id; title = topic.title; question = topic.question;
            }
        } else if ("CONFLICT".equals(selected.taskType) || "CANDIDATE".equals(selected.taskType)) {
            question = store.researchTarget(selected.taskId);
        } else if ("HYPOTHESIS".equals(selected.taskType)) {
            for (MemoryStore.Hypothesis h : store.hypotheses()) {
                if (selected.taskId.equals(h.id)) {
                    question = "Проверить гипотезу: " + h.rule + " Основание: " + h.evidence;
                    break;
                }
            }
        }

        if (question == null || question.trim().isEmpty()) {
            renderStatus("Не удалось восстановить текст задачи. Нажми «Найти следующую задачу» и попробуй ещё раз.");
            return;
        }

        final String savedTopicId = topicId;
        final String savedTitle = title;
        final String savedQuestion = question;
        researchRunning = true;
        researchButton.setEnabled(false);
        scanButton.setEnabled(false);
        renderStatus("Исследую: " + title + ". Ищу независимые источники; это может занять до минуты.");
        new Thread(() -> {
            try {
                OpenAIWebTeacher.Result result = OpenAIWebTeacher.research(apiKey, savedQuestion, store);
                if (result.report == null || result.report.trim().isEmpty()) {
                    runOnUiThread(() -> {
                        renderStatus("Поиск не вернул отчёта. Задача не отмечена выполненной; проверь API и повтори.");
                        setResearchText(InitiativeResearchAgenda.latestResultText(this));
                        researchRunning = false; researchButton.setEnabled(true); scanButton.setEnabled(true);
                    });
                    return;
                }
                InitiativeResearchAgenda.saveResult(this, savedTopicId, savedTitle, savedQuestion, result.report, result.urls);
                runOnUiThread(() -> {
                    setResearchText(InitiativeResearchAgenda.latestResultText(this));
                    refreshAgenda();
                    renderStatus("Исследование сохранено. Найдено URL: " + (result.urls == null ? 0 : result.urls.length)
                            + ". Память фактов не изменялась.");
                    render(InitiativeEngine.scan(this));
                    researchRunning = false; researchButton.setEnabled(true); scanButton.setEnabled(true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    String message = e.getMessage();
                    if (message == null || message.trim().isEmpty()) message = e.getClass().getSimpleName();
                    if (message.length() > 300) message = message.substring(0, 300) + "…";
                    renderStatus("Веб-исследование не удалось: " + message + ". Проверь ключ, доступ к API и квоту.");
                    researchRunning = false; researchButton.setEnabled(true); scanButton.setEnabled(true);
                });
            }
        }).start();
    }

    private void refreshInitiative() {
        InitiativeEngine.Report report = InitiativeEngine.scan(this);
        render(report);
        refreshAgenda();
        setResearchText(InitiativeResearchAgenda.latestResultText(this));
    }

    private void refreshAgenda() {
        if (agendaView != null) agendaView.setText(InitiativeResearchAgenda.listText(this));
    }

    private void setResearchText(String text) {
        if (researchView == null) return;
        researchView.setText(text);
        Linkify.addLinks(researchView, Linkify.WEB_URLS);
        researchView.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void renderStatus(String value) {
        if (status != null) status.setText(value);
    }

    private void render(InitiativeEngine.Report report) {
        if (report == null) {
            reportView.setText("Пока нет отчёта. Нажми «Найти следующую задачу».");
            return;
        }
        reportView.setText(report.displayText());
        renderStatus(InitiativeScheduler.isEnabled(this)
                ? "Ежедневное исследование включено."
                : "Ежедневное исследование выключено.");
    }
}
