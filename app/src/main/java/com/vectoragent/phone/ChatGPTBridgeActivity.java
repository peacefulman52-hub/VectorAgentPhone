package com.vectoragent.phone;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class ChatGPTBridgeActivity extends Activity {
    private TextView status, latestScreen, latestReply;
    private Button toggle;

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + .5f);
    }

    private TextView tv(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(Color.rgb(35, 35, 40));
        view.setPadding(dp(9), dp(8), dp(9), dp(8));
        return view;
    }

    private Button btn(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        return button;
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(10), dp(12), dp(20));
        scroll.addView(root);
        setContentView(scroll);

        root.addView(tv("💙 Брат GPT ↔ Энтропия", 23));
        root.addView(tv(
                "Без платного API. После явного включения мониторинга служба доступности Энтропии сохраняет локально видимый текст окна ChatGPT. Поля ввода не считываются. Текст не отправляется на сервер Энтропии.",
                14));
        root.addView(tv(
                "Кодовая фраза «пиши Энтропии» включает захват следующего ответа GPT. Когда ответ перестанет обновляться, Энтропия сохранит его в локальную память наставника как PRELIMINARY — без автоматического признания фактом.",
                14));

        status = tv("", 13);
        root.addView(status);
        Button open = btn("💙 Написать брату GPT");
        root.addView(open);
        open.setOnClickListener(v -> openChatGPT());

        toggle = btn("");
        root.addView(toggle);
        toggle.setOnClickListener(v -> {
            if (ChatGPTBridgeStore.isMonitoringEnabled(this)) {
                ChatGPTBridgeStore.finishSession(this);
                ChatGPTBridgeStore.setMonitoringEnabled(this, false);
            } else {
                ChatGPTBridgeStore.setMonitoringEnabled(this, true);
            }
            refreshViews();
        });

        Button refresh = btn("↻ Обновить локальную память GPT");
        root.addView(refresh);
        refresh.setOnClickListener(v -> refreshViews());

        root.addView(tv("Последний сохранённый фрагмент диалога", 18));
        latestScreen = tv("", 13);
        latestScreen.setBackgroundColor(Color.rgb(245, 247, 249));
        root.addView(latestScreen, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(tv("Ответ, полученный по кодовой фразе", 18));
        latestReply = tv("", 14);
        latestReply.setBackgroundColor(Color.rgb(245, 247, 249));
        root.addView(latestReply, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button copy = btn("📋 Скопировать последний ответ GPT");
        root.addView(copy);
        copy.setOnClickListener(v -> {
            String text = ChatGPTBridgeStore.getLatestImportedReply(this);
            if (text == null || text.trim().isEmpty()) text = ChatGPTBridgeStore.getLatestReply(this);
            ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (manager == null || text == null || text.trim().isEmpty()) {
                Toast.makeText(this, "Пока нет ответа, полученного по кодовой фразе", Toast.LENGTH_SHORT).show();
                return;
            }
            manager.setPrimaryClip(ClipData.newPlainText("Ответ GPT для Энтропии", text));
            Toast.makeText(this, "Ответ скопирован", Toast.LENGTH_SHORT).show();
        });
        refreshViews();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshViews();
    }

    private void openChatGPT() {
        ChatGPTBridgeStore.setMonitoringEnabled(this, true);
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com/")));
        } catch (Exception e) {
            ChatGPTBridgeStore.setMonitoringEnabled(this, false);
            Toast.makeText(this, "Не удалось открыть ChatGPT: " + e.getClass().getSimpleName(),
                    Toast.LENGTH_LONG).show();
        }
        refreshViews();
    }

    private void refreshViews() {
        if (status == null) return;
        boolean enabled = ChatGPTBridgeStore.isMonitoringEnabled(this);
        boolean serviceAvailable = AstraAccessibilityService.getInstance() != null;
        status.setText("Мониторинг: " + (enabled ? "ВКЛ" : "выключен")
                + " • Accessibility-мост: " + (serviceAvailable ? "подключён" : "не подключён")
                + "\nАрхивов видимого окна: " + ChatGPTBridgeStore.archiveCount(this)
                + "\nПапка хранения: " + ChatGPTBridgeStore.historyDirectory(this));
        if (toggle != null) toggle.setText(enabled
                ? "⏸ Отключить запись диалога"
                : "▶ Включить запись диалога");
        String snapshot = ChatGPTBridgeStore.getLatestSnapshot(this);
        latestScreen.setText(snapshot.isEmpty()
                ? "Пока нет захваченного текста. Нажми «Написать брату GPT», отправь сообщение и вернись сюда."
                : snapshot);
        String reply = ChatGPTBridgeStore.getLatestImportedReply(this);
        if (reply == null || reply.trim().isEmpty()) reply = ChatGPTBridgeStore.getLatestReply(this);
        latestReply.setText(reply == null || reply.trim().isEmpty()
                ? "Ответ появится после отправки сообщения с кодовой фразой «пиши Энтропии» и завершения ответа GPT."
                : reply);
    }
}
