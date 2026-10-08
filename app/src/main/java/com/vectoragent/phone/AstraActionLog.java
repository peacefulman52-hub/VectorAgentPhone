package com.vectoragent.phone;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public final class AstraActionLog {
    private static final String PREF = "astra_bridge";
    private static final String KEY = "actions";
    private final SharedPreferences prefs;

    public AstraActionLog(Context c) {
        prefs = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public synchronized void add(String stage, AstraCommand command, String result) {
        JSONArray a = read();
        JSONObject o = new JSONObject();
        try {
            o.put("time", System.currentTimeMillis());
            o.put("stage", stage);
            o.put("command", command.toJson());
            o.put("result", result == null ? "" : result);
            a.put(o);
            while (a.length() > 300) a.remove(0);
            prefs.edit().putString(KEY, a.toString()).apply();
        } catch (Exception ignored) {}
    }

    public synchronized List<String> recent(int max) {
        List<String> out = new ArrayList<>();
        JSONArray a = read();
        int from = Math.max(0, a.length() - Math.max(1, max));
        for (int i = from; i < a.length(); i++) {
            try {
                JSONObject o = a.getJSONObject(i);
                JSONObject c = o.optJSONObject("command");
                String action = c == null ? "" : c.optString("action");
                String target = c == null ? "" : c.optString("target");
                out.add(o.optString("stage") + " | " + action +
                        (target.isEmpty() ? "" : " | " + target) +
                        " | " + o.optString("result"));
            } catch (Exception ignored) {}
        }
        return out;
    }

    public synchronized int size() { return read().length(); }

    public synchronized void clear() { prefs.edit().remove(KEY).apply(); }

    private JSONArray read() {
        try { return new JSONArray(prefs.getString(KEY, "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }
}
