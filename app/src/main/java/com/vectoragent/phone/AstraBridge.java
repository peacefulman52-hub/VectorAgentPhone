package com.vectoragent.phone;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import org.json.JSONObject;

public final class AstraBridge {
    private final Context context;
    private final AstraActionLog log;
    private volatile boolean stopped;

    public AstraBridge(Context c) {
        context = c;
        log = new AstraActionLog(c.getApplicationContext());
        stopped = false;
    }

    public boolean isServiceConnected() {
        return AstraAccessibilityService.getInstance() != null;
    }

    public boolean isStopped() {
        AstraAccessibilityService s = AstraAccessibilityService.getInstance();
        return stopped || (s != null && s.isStopped());
    }

    public void stop() {
        stopped = true;
        AstraAccessibilityService s = AstraAccessibilityService.getInstance();
        if (s != null) s.stopExecution();
        AstraCommand cmd = new AstraCommand(AstraCommand.Action.STOP, "", "", false);
        safeLog("STOP", cmd, "Выполнение остановлено.");
    }

    public void resume() {
        stopped = false;
        AstraAccessibilityService s = AstraAccessibilityService.getInstance();
        if (s != null) s.resumeExecution();
        safeLog("RESUME", new AstraCommand(AstraCommand.Action.STOP, "", "", false),
                "Выполнение возобновлено.");
    }

    public String execute(AstraCommand command) {
        if (command == null) return "Команда отсутствует.";

        try {
            if (command.action != AstraCommand.Action.STOP && isStopped()) {
                String r = "STOP активирован.";
                safeLog("BLOCKED", command, r);
                return r;
            }

            String result;
            switch (command.action) {
                case OPEN_URL:
                    result = openUrl(command.target);
                    break;
                case CLICK:
                    result = requireService(command, true);
                    break;
                case TYPE:
                    result = requireService(command, false);
                    break;
                case READ_SCREEN:
                    AstraAccessibilityService s = AstraAccessibilityService.getInstance();
                    result = s == null ? "Accessibility Service не подключён." : s.readScreen();
                    break;
                case STOP:
                default:
                    stop();
                    result = "STOP активирован.";
                    break;
            }
            safeLog("EXECUTE", command, result);
            return result;
        } catch (Throwable t) {
            String result = "ASTRA ERROR: " + t.getClass().getSimpleName() +
                    (t.getMessage() == null ? "" : " — " + t.getMessage());
            safeLog("CRASH_GUARD", command, result);
            return result;
        }
    }

    private String requireService(AstraCommand command, boolean click) {
        AstraAccessibilityService s = AstraAccessibilityService.getInstance();
        if (s == null) return "Accessibility Service не подключён.";
        return click ? s.click(command.target) : s.type(command.target, command.value);
    }

    private String openUrl(String url) {
        String u = url == null ? "" : url.trim();
        if (u.isEmpty()) return "URL пуст.";
        if (!u.startsWith("https://") && !u.startsWith("http://")) {
            return "Разрешены только http/https URL.";
        }

        Uri uri = Uri.parse(u);

        try {
            PackageManager pm = context.getPackageManager();
            Intent chrome = new Intent(Intent.ACTION_VIEW, uri);
            chrome.setPackage("com.android.chrome");
            if (chrome.resolveActivity(pm) != null) {
                startActivity(chrome);
                return "Chrome открыт: " + u;
            }
        } catch (Throwable ignored) {
            // Fallback below.
        }

        try {
            Intent browser = new Intent(Intent.ACTION_VIEW, uri);
            if (browser.resolveActivity(context.getPackageManager()) == null) {
                return "На телефоне нет приложения для открытия ссылок.";
            }
            startActivity(browser);
            return "Открыт браузер: " + u;
        } catch (Throwable e) {
            return "Не удалось открыть URL: " +
                    e.getClass().getSimpleName() +
                    (e.getMessage() == null ? "" : " — " + e.getMessage());
        }
    }

    private void startActivity(Intent intent) {
        if (context instanceof Activity) {
            ((Activity) context).startActivity(intent);
        } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    private void safeLog(String stage, AstraCommand command, String result) {
        try {
            log.add(stage, command, result);
        } catch (Throwable ignored) {
        }
    }

    public String proposedCommand(JSONObject json) {
        try {
            return AstraCommand.fromJson(json).toJson().toString(2);
        } catch (Exception e) {
            return "Некорректная команда: " + e.getMessage();
        }
    }

    public java.util.List<String> recentLog(int max) { return log.recent(max); }
    public int logSize() { return log.size(); }
    public void clearLog() { log.clear(); }
}
