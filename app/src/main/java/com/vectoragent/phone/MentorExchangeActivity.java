package com.vectoragent.phone;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * No-API-key bridge for exchanging research with a human-operated ChatGPT session.
 * Imported mentor replies are always preliminary unless the user separately confirms
 * a working decision; they never become verified facts automatically.
 */
public final class MentorExchangeActivity extends Activity {
    private EditText replyInput;
    private CheckBox confirmDecision;
    private TextView status, latestReply;
    private Button processReply;

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }

    private TextView tv(String text, int size) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(Color.rgb(35, 35, 40));
        t.setPadding(dp(8), dp(8), dp(8), dp(8));
        return t;
    }

    private Button btn(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        return b;
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        MentorExchangeStore.ensureFiles(this);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(10), dp(12), dp(18));
        scroll.addView(root);
        setContentView(scroll);

        root.addView(tv("🤝 ЭНТРОПИЯ ↔ НАСТАВНИК", 23));
        root.addView(tv(
                "Бесплатный ручной обмен без API-ключа. Нажми «Подготовить контекст и передать в ChatGPT», затем выбери ChatGPT. После ответа скопируй его, вернись сюда и вставь в большое поле. Затем обязательно нажми «Обработать ответ наставника».",
                14));
        root.addView(tv(
                "Приватность: текст не отправляется автоматически. Он передаётся другому приложению только после твоего нажатия и выбора получателя. Не включай в отчёт пароли, ключи и личные секреты.",
                13));

        status = tv("Ответы наставника сохраняются как PRELIMINARY. Фактическая память и VERIFIED_FACTS автоматически не изменяются.", 13);
        root.addView(status);

        Button share = btn("📨 Подготовить контекст и передать в ChatGPT");
        root.addView(share);
        share.setOnClickListener(v -> sharePrompt());

        Button copyPrompt = btn("📋 Скопировать запрос наставнику");
        root.addView(copyPrompt);
        copyPrompt.setOnClickListener(v -> {
            String prompt = MentorExchangeStore.buildMentorPrompt(this);
            if (putClipboard("Контекст Энтропии для наставника", prompt)) {
                status.setText("Запрос скопирован. Открой ChatGPT и вставь текст в сообщение.");
                Toast.makeText(this, "Контекст наставнику скопирован", Toast.LENGTH_SHORT).show();
            } else {
                status.setText("Не удалось открыть буфер обмена.");
            }
        });

        root.addView(tv("Вставь сюда полный ответ ChatGPT", 18));
        replyInput = new EditText(this);
        replyInput.setHint("Долгое нажатие → Вставить ответ наставника");
        replyInput.setGravity(Gravity.TOP | Gravity.START);
        replyInput.setMinLines(7);
        replyInput.setTextSize(14);
        root.addView(replyInput, new LinearLayout.LayoutParams(-1, dp(210)));

        confirmDecision = new CheckBox(this);
        confirmDecision.setText("Я подтверждаю: сохранить рекомендацию в DECISIONS.md");
        confirmDecision.setTextSize(14);
        root.addView(confirmDecision);

        processReply = btn("🧠 Обработать ответ наставника");
        processReply.setEnabled(false);
        root.addView(processReply);
        processReply.setOnClickListener(v -> processMentorReply());
        replyInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                processReply.setEnabled(s != null && s.toString().trim().length() >= 20);
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        root.addView(tv("Последний сохранённый ответ", 18));
        latestReply = tv(MentorExchangeStore.latestReply(this), 13);
        root.addView(latestReply);

        Button copyLatest = btn("📋 Скопировать последний сохранённый ответ");
        root.addView(copyLatest);
        copyLatest.setOnClickListener(v -> {
            if (putClipboard("Последний ответ наставника", MentorExchangeStore.latestReply(this))) {
                Toast.makeText(this, "Ответ наставника скопирован", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Не удалось открыть буфер обмена", Toast.LENGTH_SHORT).show();
            }
        });

        Button refresh = btn("↻ Обновить последний сохранённый ответ");
        root.addView(refresh);
        refresh.setOnClickListener(v -> latestReply.setText(MentorExchangeStore.latestReply(this)));
    }

    private void sharePrompt() {
        String prompt = MentorExchangeStore.buildMentorPrompt(this);
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, prompt);
        try {
            startActivity(Intent.createChooser(send, "Передать контекст наставнику"));
            status.setText("Меню отправки открыто. Выбери ChatGPT или скопируй запрос и вставь его вручную.");
        } catch (Exception e) {
            if (putClipboard("Контекст Энтропии для наставника", prompt)) {
                status.setText("Меню отправки недоступно; запрос скопирован в буфер обмена.");
            } else {
                status.setText("Не удалось открыть меню отправки или буфер обмена.");
            }
        }
    }

    private void processMentorReply() {
        String reply = replyInput.getText() == null ? "" : replyInput.getText().toString();
        String result = MentorExchangeStore.importMentorReply(this, reply, confirmDecision.isChecked());
        status.setText(result);
        Toast.makeText(this, result, Toast.LENGTH_LONG).show();
        if (result.startsWith("Ответ сохранён")) {
            replyInput.setText("");
            confirmDecision.setChecked(false);
            latestReply.setText(MentorExchangeStore.latestReply(this));
            processReply.setEnabled(false);
        }
    }

    private boolean putClipboard(String label, String text) {
        ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager == null || text == null || text.trim().isEmpty()) return false;
        manager.setPrimaryClip(ClipData.newPlainText(label, text));
        return true;
    }
}
