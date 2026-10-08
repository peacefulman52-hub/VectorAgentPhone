package com.vectoragent.phone;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AstraAccessibilityService extends AccessibilityService {
    private static volatile AstraAccessibilityService instance;
    private volatile boolean stopped;

    public static AstraAccessibilityService getInstance() { return instance; }
    public boolean isStopped() { return stopped; }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        stopped = false;
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED |
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS |
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        setServiceInfo(info);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override
    public void onInterrupt() { stopped = true; }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    public void stopExecution() { stopped = true; }
    public void resumeExecution() { stopped = false; }

    public String readScreen() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return "Экран недоступен.";
        StringBuilder out = new StringBuilder();
        appendNode(root, out, 0);
        String s = out.toString().trim();
        return s.isEmpty() ? "На активном экране нет доступного текста." : s;
    }

    private void appendNode(AccessibilityNodeInfo node, StringBuilder out, int depth) {
        if (node == null || depth > 30) return;
        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        String line = text != null ? text.toString().trim() : "";
        if (line.isEmpty() && desc != null) line = desc.toString().trim();
        if (!line.isEmpty()) {
            if (out.length() > 0) out.append("\n");
            out.append(line);
        }
        for (int i = 0; i < node.getChildCount(); i++) appendNode(node.getChild(i), out, depth + 1);
    }

    public String click(String target) {
        if (stopped) return "STOP активирован.";
        AccessibilityNodeInfo root = getRootInActiveWindow();
        AccessibilityNodeInfo node = find(root, target);
        if (node == null) return "Элемент не найден: " + target;
        // Some accessibility nodes expose ACTION_CLICK without advertising
        // isClickable(), so try the action itself first.
        if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return "CLICK выполнен: " + target;
        }

        // If the matched text belongs to a child label/icon, walk up the
        // hierarchy and try clickable/action-capable parents.
        AccessibilityNodeInfo parent = node.getParent();
        int depth = 0;
        while (parent != null && depth++ < 8) {
            if (parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return "CLICK выполнен через родительский узел: " + target;
            }
            parent = parent.getParent();
        }

        return "Элемент найден, но ACTION_CLICK недоступен: " + target;
    }

    public String type(String target, String text) {
        if (stopped) return "STOP активирован.";
        AccessibilityNodeInfo root = getRootInActiveWindow();
        AccessibilityNodeInfo node = find(root, target);
        if (node == null) return "Поле не найдено: " + target;
        if (!node.isEditable()) {
            AccessibilityNodeInfo parent = node.getParent();
            if (parent != null && parent.isEditable()) node = parent;
        }
        if (!node.isEditable()) return "Поле не редактируемое: " + target;
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        boolean ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
        return ok ? "TYPE выполнен." : "Не удалось установить текст.";
    }

    private AccessibilityNodeInfo find(AccessibilityNodeInfo root, String target) {
        if (root == null) return null;
        String q = target == null ? "" : target.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return null;
        List<AccessibilityNodeInfo> byText = root.findAccessibilityNodeInfosByText(target);
        if (byText != null) {
            for (AccessibilityNodeInfo n : byText) if (matches(n, q)) return n;
        }
        return findRecursive(root, q);
    }

    private AccessibilityNodeInfo findRecursive(AccessibilityNodeInfo n, String q) {
        if (n == null) return null;
        if (matches(n, q)) return n;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo hit = findRecursive(n.getChild(i), q);
            if (hit != null) return hit;
        }
        return null;
    }

    private boolean matches(AccessibilityNodeInfo n, String q) {
        CharSequence t = n.getText();
        CharSequence d = n.getContentDescription();
        String a = t == null ? "" : t.toString().toLowerCase(Locale.ROOT);
        String b = d == null ? "" : d.toString().toLowerCase(Locale.ROOT);
        String v = n.getViewIdResourceName() == null ? "" :
                n.getViewIdResourceName().toLowerCase(Locale.ROOT);
        return a.equals(q) || b.equals(q) || v.equals(q) || a.contains(q) || b.contains(q) || v.contains(q);
    }
}
