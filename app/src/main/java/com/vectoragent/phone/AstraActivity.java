package com.vectoragent.phone;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.*;

import org.json.JSONObject;
import java.util.List;

public class AstraActivity extends Activity {
    private AstraBridge bridge;
    private LinearLayout root;
    private TextView status, result, logView;
    private EditText target, value;

    int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
    TextView tv(String s, float z) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(z); t.setTextColor(Color.rgb(35,35,40));
        t.setPadding(dp(8),dp(7),dp(8),dp(7)); return t;
    }
    Button btn(String s) { Button b=new Button(this); b.setText(s); b.setAllCaps(false); return b; }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        bridge = new AstraBridge(this);
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12),dp(10),dp(12),dp(18)); scroll.addView(root);
        setContentView(scroll);
        build();
    }

    @Override protected void onResume() {
        super.onResume();
        if (bridge != null) refresh();
    }

    void build() {
        root.addView(tv("🛰 ASTRA BRIDGE 0.2", 26));
        root.addView(tv("Безопасный исполнительный слой телефона. Команды проходят через явный EXECUTE; действия пишутся в журнал.", 13));

        status = tv("", 15); root.addView(status);
        LinearLayout setup = new LinearLayout(this);
        Button enable = btn("⚙ Включить Accessibility");
        Button refresh = btn("↻ Обновить");
        setup.addView(enable, new LinearLayout.LayoutParams(0,-2,1));
        setup.addView(refresh, new LinearLayout.LayoutParams(0,-2,1));
        root.addView(setup);

        enable.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        refresh.setOnClickListener(v -> refresh());

        root.addView(tv("Команды", 18));
        root.addView(tv("OPEN_URL — открыть HTTPS/HTTP адрес\nCLICK — нажать элемент по text / description / id\nTYPE — ввести текст в поле\nREAD_SCREEN — снять доступный текст экрана", 12));

        EditText action = new EditText(this);
        action.setHint("Действие: OPEN_URL / CLICK / TYPE / READ_SCREEN");
        root.addView(action);
        target = new EditText(this); target.setHint("Target / URL / название элемента");
        root.addView(target);
        value = new EditText(this); value.setHint("Text для TYPE (для остальных пусто)");
        root.addView(value);

        Button propose = btn("📋 Сформировать команду");
        Button execute = btn("▶ EXECUTE");
        root.addView(propose); root.addView(execute);

        propose.setOnClickListener(v -> {
            try {
                String a = action.getText().toString().trim().toUpperCase();
                if (a.isEmpty()) a = "READ_SCREEN";
                AstraCommand.Action.valueOf(a);
                JSONObject o = new JSONObject();
                o.put("action", a); o.put("target", target.getText().toString().trim());
                o.put("value", value.getText().toString());
                o.put("requires_confirmation", true);
                result.setText("Предложенная команда:\n" + bridge.proposedCommand(o));
            } catch (Throwable e) {
                result.setText("Ошибка формирования команды: " + e.getClass().getSimpleName() +
                        " — " + String.valueOf(e.getMessage()));
            }
        });

        execute.setOnClickListener(v -> {
            try {
                String a = action.getText().toString().trim().toUpperCase();
                if (a.isEmpty()) a = "READ_SCREEN";
                AstraCommand.Action act = AstraCommand.Action.valueOf(a);
                AstraCommand cmd = new AstraCommand(act, target.getText().toString().trim(), value.getText().toString(), true);
                result.setText("Результат:\n" + bridge.execute(cmd));
                refresh();
            } catch (Throwable e) {
                result.setText("Ошибка EXECUTE: " + e.getClass().getSimpleName() +
                        " — " + String.valueOf(e.getMessage()));
                refresh();
            }
        });

        LinearLayout quick = new LinearLayout(this);
        Button chrome = btn("🌐 Открыть Chrome: GitHub");
        Button read = btn("👁 READ_SCREEN");
        Button stop = btn("🛑 STOP");
        Button resume = btn("▶ RESUME");
        quick.setOrientation(LinearLayout.VERTICAL);
        quick.addView(chrome); quick.addView(read); quick.addView(stop); quick.addView(resume);
        root.addView(quick);

        chrome.setOnClickListener(v -> executeQuick(new AstraCommand(
                AstraCommand.Action.OPEN_URL,
                "https://github.com/peacefulman52-hub/VectorAgentPhone", "", true)));

        read.setOnClickListener(v -> executeQuick(new AstraCommand(
                AstraCommand.Action.READ_SCREEN, "", "", true)));

        stop.setOnClickListener(v -> {
            try { bridge.stop(); result.setText("STOP активирован."); }
            catch (Throwable e) { result.setText("STOP error: " + e.getMessage()); }
            refresh();
        });

        resume.setOnClickListener(v -> {
            try { bridge.resume(); result.setText("Выполнение возобновлено."); }
            catch (Throwable e) { result.setText("RESUME error: " + e.getMessage()); }
            refresh();
        });

        root.addView(tv("Журнал действий", 18));
        logView = tv("", 12); root.addView(logView);
        Button clear = btn("Очистить журнал"); root.addView(clear);
        clear.setOnClickListener(v -> { bridge.clearLog(); refresh(); });
    }

    private void executeQuick(AstraCommand cmd) {
        try {
            String value = bridge.execute(cmd);
            result.setText("Результат:\n" + value);
        } catch (Throwable e) {
            result.setText("ASTRA crash guard: " + e.getClass().getSimpleName() +
                    " — " + String.valueOf(e.getMessage()));
        }
        refresh();
    }

    void refresh() {
        if (status == null) return;
        try {
            String service = bridge.isServiceConnected() ? "CONNECTED" : "OFF";
            String mode = bridge.isStopped() ? "STOPPED" : "READY";
            status.setText("Service: " + service + "    Mode: " + mode + "\n" +
                    "Логов: " + bridge.logSize());

            List<String> rows = bridge.recentLog(12);
            StringBuilder s = new StringBuilder();
            for (String x : rows) s.append("• ").append(x).append("\n");
            logView.setText(s.length() == 0 ? "Пока действий нет." : s.toString());
        } catch (Throwable e) {
            status.setText("ASTRA UI ERROR: " + e.getClass().getSimpleName());
        }
    }
}
