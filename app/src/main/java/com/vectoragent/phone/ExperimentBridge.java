package com.vectoragent.phone;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;

/**
 * v0.9.1 experimental bridge.
 *
 * Keeps a local, append-only experiment journal around MemoryStore operations.
 * It deliberately does not mutate MemoryStore directly except through its public
 * learning API, so experiments remain reproducible and auditable.
 */
public class ExperimentBridge {
    private static final String PREF = "vector_experiments";
    private static final String KEY = "log";
    private final Context context;
    private final MemoryStore store;

    public ExperimentBridge(Context context, MemoryStore store) {
        this.context = context.getApplicationContext();
        this.store = store;
    }

    public String runIngest(String input, double confidence) {
        String id = "exp-" + System.currentTimeMillis();
        String before = store.exportJson();
        MemoryStore.IngestResult result = store.ingest(input, confidence);
        String after = store.exportJson();

        JSONObject event = new JSONObject();
        try {
            event.put("id", id);
            event.put("type", "INGEST");
            event.put("input", input);
            event.put("confidence", confidence);
            event.put("added", result.added);
            event.put("similar", result.similar);
            event.put("conflicts", result.conflicts);
            event.put("ignored", result.ignored);
            event.put("before", before);
            event.put("after", after);
            event.put("learning", new JSONArray(result.learning));
            event.put("messages", new JSONArray(result.messages));
            event.put("timestamp", System.currentTimeMillis());
            append(event);
        } catch (Exception ignored) {}

        return id;
    }

    public String runLearn(String memoryId, boolean accepted) {
        String id = "exp-" + System.currentTimeMillis();
        String before = store.exportJson();
        store.learn(memoryId, accepted);
        String after = store.exportJson();

        JSONObject event = new JSONObject();
        try {
            event.put("id", id);
            event.put("type", "LEARN");
            event.put("memoryId", memoryId);
            event.put("accepted", accepted);
            event.put("before", before);
            event.put("after", after);
            event.put("timestamp", System.currentTimeMillis());
            append(event);
        } catch (Exception ignored) {}

        return id;
    }

    public String snapshotState() {
        return store.exportJson();
    }

    public String exportLog() {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getString(KEY, "[]");
    }

    public void clearLog() {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().remove(KEY).apply();
    }

    private synchronized void append(JSONObject event) {
        try {
            String raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                    .getString(KEY, "[]");
            JSONArray a = new JSONArray(raw);
            a.put(event);
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                    .edit().putString(KEY, a.toString()).apply();
        } catch (Exception ignored) {}
    }
}
