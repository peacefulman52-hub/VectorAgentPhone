package com.vectoragent.phone;

import org.json.JSONObject;

import java.util.Locale;

public final class AstraCommand {
    public enum Action { OPEN_URL, CLICK, TYPE, READ_SCREEN, STOP, RESUME, END_TASK }

    public final Action action;
    public final String target;
    public final String value;
    public final boolean requiresConfirmation;
    public final String taskId;
    public final int clickLimit;

    public AstraCommand(Action action, String target, String value, boolean requiresConfirmation) {
        this(action, target, value, requiresConfirmation, "", 0);
    }

    public AstraCommand(Action action, String target, String value, boolean requiresConfirmation,
                        String taskId, int clickLimit) {
        this.action = action;
        this.target = target == null ? "" : target;
        this.value = value == null ? "" : value;
        this.requiresConfirmation = requiresConfirmation;
        this.taskId = taskId == null ? "" : taskId.trim();
        this.clickLimit = Math.max(0, clickLimit);
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("action", action.name());
            o.put("target", target);
            o.put("value", value);
            o.put("requires_confirmation", requiresConfirmation);
            o.put("task_id", taskId);
            o.put("click_limit", clickLimit);
        } catch (Exception ignored) {}
        return o;
    }

    public static AstraCommand fromJson(JSONObject o) {
        String rawAction = o.optString("action", "READ_SCREEN");
        Action action = Action.valueOf(rawAction.toUpperCase(Locale.ROOT));
        String taskId = o.optString("task_id", "").trim();
        int clickLimit = Math.max(0, o.optInt("click_limit", 0));
        return new AstraCommand(action, o.optString("target", ""), o.optString("value", ""),
                o.optBoolean("requires_confirmation", true), taskId, clickLimit);
    }
}
