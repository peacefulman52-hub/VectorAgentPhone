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
    private EditText target, value, relayUrl, relayDevice, relayToken;
    private TextView relayStatus, pending;
    private AstraRelayClient relay;
    private android.content.SharedPreferences relayPrefs;

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
        relayPrefs = getSharedPreferences("astra_link", MODE_PRIVATE);
        relay = new AstraRelayClient(new AstraRelayClient.Listener() {
            @Override public void onStatus(String s) { runOnUiThread(() -> { if (relayStatus != null) relayStatus.setText("Relay: " + s); }); }
            @Override public void onCommand(JSONObject command) { runOnUiThread(() -> handleRemoteCommand(command)); }
            @Override public void onError(String error) { runOnUiThread(() -> { if (relayStatus != null) relayStatus.setText("Relay ERROR: " + error); }); }
        });
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
        root.addView(tv("Безопасный исполнительный слой телефона. В Chrome Astra оставляет плавающие кнопки CLICK / STOP / READ под рукой.", 13));

        status = tv("", 15); root.addView(status);
        result = tv("Результат появится здесь.", 13); root.addView(result);
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

        root.addView(tv("Astra Link — управление через relay", 18));
        root.addView(tv("Первый этап работает пока открыт Astra. Команды из relay исполняются автоматически только после включения ARM. Для опасных действий оставляем requires_confirmation.", 12));

        relayUrl = new EditText(this); relayUrl.setHint("Relay URL, например https://astra-link.example.workers.dev");
        relayDevice = new EditText(this); relayDevice.setHint("Device ID");
        relayToken = new EditText(this); relayToken.setHint("Token");
        relayToken.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(relayUrl); root.addView(relayDevice); root.addView(relayToken);

        LinearLayout relayButtons = new LinearLayout(this);
        Button connect = btn("🔗 CONNECT");
        Button disconnect = btn("⛔ DISCONNECT");
        Button arm = btn("🔐 ARM");
        relayButtons.addView(connect, new LinearLayout.LayoutParams(0,-2,1));
        relayButtons.addView(disconnect, new LinearLayout.LayoutParams(0,-2,1));
        relayButtons.addView(arm, new LinearLayout.LayoutParams(0,-2,1));
        root.addView(relayButtons);
        relayStatus = tv("Relay: OFF", 13); root.addView(relayStatus);
        pending = tv("Нет удалённых команд.", 13); root.addView(pending);

        loadRelaySettings();
        connect.setOnClickListener(v -> connectRelay());
        disconnect.setOnClickListener(v -> { relay.stop(); refreshRelayStatus(); });
        arm.setOnClickListener(v -> {
            boolean armed = relayPrefs.getBoolean("armed", false);
            relayPrefs.edit().putBoolean("armed", !armed).apply();
            arm.setText(!armed ? "🔓 DISARM" : "🔐 ARM");
            refreshRelayStatus();
        });

        LinearLayout journalHeader = new LinearLayout(this);
        journalHeader.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView journalTitle = tv("Журнал действий", 18);
        Button toggleLog = btn("▾ Показать");
        journalHeader.addView(journalTitle, new LinearLayout.LayoutParams(0,-2,1));
        journalHeader.addView(toggleLog);
        root.addView(journalHeader);

        logView = tv("", 12);
        logView.setVisibility(android.view.View.GONE);
        root.addView(logView);

        Button clear = btn("Очистить журнал");
        clear.setVisibility(android.view.View.GONE);
        root.addView(clear);

        toggleLog.setOnClickListener(v -> {
            boolean show = logView.getVisibility() != android.view.View.VISIBLE;
            logView.setVisibility(show ? android.view.View.VISIBLE : android.view.View.GONE);
            clear.setVisibility(show ? android.view.View.VISIBLE : android.view.View.GONE);
            toggleLog.setText(show ? "▴ Скрыть" : "▾ Показать");
        });
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
            status.setText("Service: " + service + "    Mode: " + mode);

            List<String> rows = bridge.recentLog(12);
            StringBuilder s = new StringBuilder();
            for (String x : rows) s.append("• ").append(x).append("\n");
            logView.setText(s.length() == 0 ? "Пока действий нет." : s.toString());
        } catch (Throwable e) {
            status.setText("ASTRA UI ERROR: " + e.getClass().getSimpleName());
        }
    }
    private void loadRelaySettings() {
        relayUrl.setText(relayPrefs.getString("url", ""));
        relayDevice.setText(relayPrefs.getString("device", ""));
        relayToken.setText(relayPrefs.getString("token", ""));
        boolean armed = relayPrefs.getBoolean("armed", false);
        // Button label is set when the UI is built.
    }

    private void connectRelay() {
        String url = relayUrl.getText().toString().trim();
        String device = relayDevice.getText().toString().trim();
        String token = relayToken.getText().toString().trim();
        relayPrefs.edit().putString("url", url).putString("device", device).putString("token", token).apply();
        relay.configure(url, device, token);
        relay.start();
        refreshRelayStatus();
    }

    private void refreshRelayStatus() {
        if (relayStatus == null) return;
        boolean armed = relayPrefs.getBoolean("armed", false);
        relayStatus.setText("Relay: " + (relay.isRunning() ? "CONNECTED" : "OFF") +
                "    ARM: " + (armed ? "ON" : "OFF"));
    }

    private void handleRemoteCommand(JSONObject json) {
        try {
            String commandId = json.optString("command_id", "");
            String action = json.optString("action", "READ_SCREEN").toUpperCase();
            String targetText = json.optString("target", "");
            String valueText = json.optString("value", "");
            boolean confirmation = json.optBoolean("requires_confirmation", true);

            pending.setText("Удалённая команда: " + action + "  " + targetText +
                    (confirmation ? "  [CONFIRM]" : "  [AUTO]"));

            boolean armed = relayPrefs.getBoolean("armed", false);
            if (!armed || confirmation) {
                result.setText("Ожидает ARM/подтверждения:\n" + json.toString(2));
                return;
            }

            AstraCommand.Action act = AstraCommand.Action.valueOf(action);
            String out = bridge.execute(new AstraCommand(act, targetText, valueText, false));
            boolean ok = !out.startsWith("ASTRA ERROR") && !out.contains("не подключён");
            relay.sendResult(commandId, ok, out);
            result.setText("Relay EXECUTE:\n" + out);
            pending.setText("Удалённая команда выполнена: " + commandId);
            refresh();
        } catch (Throwable e) {
            String err = "Remote command error: " + e.getClass().getSimpleName() +
                    " — " + String.valueOf(e.getMessage());
            result.setText(err);
            try { relay.sendResult(json == null ? "" : json.optString("command_id",""), false, err); }
            catch (Throwable ignored) {}
        }
    }

    @Override protected void onDestroy() {
        if (relay != null) relay.stop();
        super.onDestroy();
    }

}
