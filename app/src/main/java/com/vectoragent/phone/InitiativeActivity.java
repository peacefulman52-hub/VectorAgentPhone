package com.vectoragent.phone;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** UI for inspecting and controlling the proactive local initiative loop. */
public final class InitiativeActivity extends Activity {
    private static final int REQUEST_NOTIFICATIONS = 73;
    private LinearLayout root;
    private TextView status, reportView;

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
    private TextView tv(String s, int size) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(size); t.setTextColor(Color.rgb(35,35,40));
        t.setPadding(dp(8), dp(8), dp(8), dp(8)); return t;
    }
    private Button btn(String s) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); return b; }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(10), dp(12), dp(18));
        scroll.addView(root); setContentView(scroll);
        root.addView(tv("🧭 ASTRA INITIATIVE", 24));
        root.addView(tv("Система сама проверяет локальную память и выбирает следующий нерешённый вопрос. Факты не изменяются автоматически; внешние действия не выполняются.", 14));
        status = tv("", 13); root.addView(status);
        reportView = tv("", 15);
        reportView.setGravity(Gravity.START);
        root.addView(reportView);

        Button scan = btn("🔎 Проверить сейчас");
        Button enable = btn("▶ Включить ежедневную проверку");
        Button disable = btn("⏸ Выключить ежедневную проверку");
        root.addView(scan); root.addView(enable); root.addView(disable);
        scan.setOnClickListener(v -> { InitiativeEngine.Report r = InitiativeEngine.scan(this); render(r); });
        enable.setOnClickListener(v -> enableSchedule());
        disable.setOnClickListener(v -> {
            InitiativeScheduler.disable(this);
            renderStatus("Ежедневная проверка выключена.");
        });
        renderStatus(InitiativeScheduler.isEnabled(this) ? "Ежедневная проверка включена." : "Ежедневная проверка выключена.");
        InitiativeEngine.Report existing = InitiativeEngine.readLatest(this);
        render(existing != null ? existing : InitiativeEngine.scan(this));
    }

    @Override protected void onResume() {
        super.onResume();
        renderStatus(InitiativeScheduler.isEnabled(this) ? "Ежедневная проверка включена." : "Ежедневная проверка выключена.");
    }

    private void enableSchedule() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        } else {
            InitiativeScheduler.enable(this);
            renderStatus("Ежедневная проверка включена. Android может сдвигать время запуска ради экономии батареи.");
            Toast.makeText(this, "Инициатива включена", Toast.LENGTH_SHORT).show();
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_NOTIFICATIONS) return;
        // The user has explicitly enabled the feature. Scans may still run if notifications are denied.
        InitiativeScheduler.enable(this);
        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        renderStatus(granted
                ? "Ежедневная проверка включена; уведомления разрешены."
                : "Ежедневная проверка включена, но уведомления запрещены системой. Отчёт останется внутри приложения.");
    }

    private void renderStatus(String value) {
        if (status != null) status.setText(value);
    }

    private void render(InitiativeEngine.Report report) {
        if (report == null) {
            reportView.setText("Пока нет сохранённого отчёта. Нажми «Проверить сейчас».");
            return;
        }
        reportView.setText(report.displayText());
        renderStatus(InitiativeScheduler.isEnabled(this)
                ? "Ежедневная проверка включена."
                : "Ежедневная проверка выключена.");
    }
}
