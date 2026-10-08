package com.vectoragent.phone;

import org.json.JSONObject;

public final class AstraCommand {
    public enum Action { OPEN_URL, CLICK, TYPE, READ_SCREEN, STOP, RESUME }

    public final Action action;
    public final String target;
    public final String value;
    public final boolean requiresConfirmation;

    public AstraCommand(Action action, String target, String value, boolean requiresConfirmation) {
        this.action = action;
        this.target = target == null ? "" : target;
        this.value = value == null ? "" : value;
        this.requiresConfirmation = requiresConfirmation;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("action", action.name());
            o.put("target", target);
            o.put("value", value);
            o.put("requires_confirmation", requiresConfirmation);
        } catch (Exception ignored) {}
        return o;
    }

    public static AstraCommand fromJson(JSONObject o) {
        Action action = Action.valueOf(o.optString("action", "READ_SCREEN").toUpperCase());
        return new AstraCommand(action, o.optString("target", ""), o.optString("value", ""),
                o.optBoolean("requires_confirmation", true));
    }
}
