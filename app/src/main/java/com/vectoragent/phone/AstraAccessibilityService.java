package com.vectoragent.phone;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class AstraAccessibilityService extends AccessibilityService {
    private static volatile AstraAccessibilityService instance;
    private volatile boolean stopped;
    private volatile boolean clickPaused;
    private volatile int clickCount;
    private volatile int clickCheckpoint = 10;
    private volatile AccessibilityNodeInfo lastExternalRoot;
    private volatile String lastExternalPackage = "";
    private AstraOverlayController overlay;

    public static final class ClickTarget {
        public final String label;
        public final String target;
        public ClickTarget(String label, String target) {
            this.label = label;
            this.target = target;
        }
    }

    public static AstraAccessibilityService getInstance() { return instance; }
    public boolean isStopped() { return stopped; }
    public boolean isClickPaused() { return clickPaused; }
    public int getClickCount() { return clickCount; }
    public int getClickCheckpoint() { return clickCheckpoint; }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        stopped = false;
        clickPaused = false;
        clickCount = 0;
        clickCheckpoint = Math.max(1, getSharedPreferences("astra_click_guard", MODE_PRIVATE)
                .getInt("checkpoint", 10));

        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED |
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS |
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        setServiceInfo(info);

        overlay = new AstraOverlayController(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        CharSequence pkg = event.getPackageName();
        String packageName = pkg == null ? "" : pkg.toString();

        if (!packageName.isEmpty() && !packageName.equals(getPackageName())) {
            if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                    event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root != null) {
                    AccessibilityNodeInfo copy = AccessibilityNodeInfo.obtain(root);
                    AccessibilityNodeInfo old = lastExternalRoot;
                    lastExternalRoot = copy;
                    lastExternalPackage = packageName;
                    if (old != null) old.recycle();
                }
                if (overlay != null) overlay.show();
            }
        } else if (packageName.equals(getPackageName())) {
            if (overlay != null) overlay.hide();
        }
    }

    @Override
    public void onInterrupt() {
        stopped = true;
        if (overlay != null) overlay.update();
    }

    @Override
    public void onDestroy() {
        if (overlay != null) overlay.destroy();
        AccessibilityNodeInfo old = lastExternalRoot;
        lastExternalRoot = null;
        if (old != null) old.recycle();
        if (instance == this) instance = null;
        super.onDestroy();
    }

    public void stopExecution() {
        stopped = true;
        clickPaused = true;
        if (overlay != null) overlay.update();
    }

    public void resumeExecution() {
        stopped = false;
        clickPaused = false;
        clickCount = 0;
        if (overlay != null) overlay.show();
        if (overlay != null) overlay.update();
    }

    public void setClickCheckpoint(int n) {
        int value = n <= 0 ? 10 : Math.min(n, 9999);
        clickCheckpoint = value;
        clickCount = 0;
        clickPaused = false;
        getSharedPreferences("astra_click_guard", MODE_PRIVATE).edit()
                .putInt("checkpoint", value).apply();
        if (overlay != null) overlay.update();
    }

    public void continueClickBatch() {
        clickPaused = false;
        clickCount = 0;
        if (overlay != null) {
            overlay.show();
            overlay.update();
        }
    }

    public String readScreen() {
        AccessibilityNodeInfo root = obtainExternalRoot();
        if (root == null) {
            root = lastExternalRoot == null ? null : AccessibilityNodeInfo.obtain(lastExternalRoot);
        }
        if (root == null) return "Экран недоступен.";

        StringBuilder out = new StringBuilder();
        appendNode(root, out, 0);
        List<ClickTarget> targets = clickableTargetsFrom(root, 25);
        root.recycle();

        String s = out.toString().trim();
        if (s.isEmpty()) s = "На последнем экране нет доступного текста.";

        StringBuilder result = new StringBuilder();
        result.append("Последнее окно: ")
                .append(lastExternalPackage.isEmpty() ? "неизвестно" : lastExternalPackage)
                .append("\n")
                .append(s);

        if (!targets.isEmpty()) {
            result.append("\n\nДоступные CLICK-цели:");
            for (ClickTarget t : targets) {
                result.append("\n• ").append(t.label).append("  [").append(t.target).append("]");
            }
        }
        return result.toString();
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
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                appendNode(child, out, depth + 1);
                child.recycle();
            }
        }
    }

    public List<ClickTarget> clickableTargets(int max) {
        AccessibilityNodeInfo root = obtainExternalRoot();
        if (root == null) {
            root = lastExternalRoot == null ? null : AccessibilityNodeInfo.obtain(lastExternalRoot);
        }
        if (root == null) return new ArrayList<>();
        List<ClickTarget> out = clickableTargetsFrom(root, max);
        root.recycle();
        return out;
    }

    private List<ClickTarget> clickableTargetsFrom(AccessibilityNodeInfo root, int max) {
        List<ClickTarget> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        collectClickable(root, out, seen, 0, Math.max(1, max));
        return out;
    }

    private void collectClickable(AccessibilityNodeInfo node, List<ClickTarget> out,
                                  Set<String> seen, int depth, int max) {
        if (node == null || depth > 30 || out.size() >= max) return;
        if (node.isClickable()) {
            String text = node.getText() == null ? "" : node.getText().toString().trim();
            String desc = node.getContentDescription() == null ? "" :
                    node.getContentDescription().toString().trim();
            String id = node.getViewIdResourceName() == null ? "" : node.getViewIdResourceName().trim();

            String label = !text.isEmpty() ? text : (!desc.isEmpty() ? desc : id);
            Rect r = new Rect();
            node.getBoundsInScreen(r);
            String target = label;
            if (target.isEmpty() && r.width() > 0 && r.height() > 0) {
                target = "@" + r.centerX() + "," + r.centerY();
                label = "Элемент " + target;
            }
            if (!target.isEmpty() && seen.add(target)) {
                String shown = label;
                if (r.width() > 0 && r.height() > 0) {
                    shown += "  (" + r.centerX() + "," + r.centerY() + ")";
                }
                out.add(new ClickTarget(shown, target));
            }
        }

        for (int i = 0; i < node.getChildCount() && out.size() < max; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                collectClickable(child, out, seen, depth + 1, max);
                child.recycle();
            }
        }
    }

    public String click(String target) {
        if (stopped) return "STOP активирован.";
        if (clickPaused) return "Клики приостановлены. Нажмите ПРОДОЛЖИТЬ.";

        String q = target == null ? "" : target.trim();
        if (q.isEmpty()) return "CLICK: цель пуста.";

        boolean success;
        if (q.startsWith("@")) {
            success = clickAt(q);
            if (!success) return "Не удалось выполнить координатный CLICK: " + q;
        } else {
            AccessibilityNodeInfo root = obtainExternalRoot();
            if (root == null) {
                root = lastExternalRoot == null ? null : AccessibilityNodeInfo.obtain(lastExternalRoot);
            }
            if (root == null) return "Экран недоступен.";

            AccessibilityNodeInfo node = find(root, q);
            if (node == null) {
                root.recycle();
                return "Элемент не найден: " + q;
            }

            success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            if (!success) {
                AccessibilityNodeInfo parent = node.getParent();
                int depth = 0;
                while (!success && parent != null && depth++ < 8) {
                    success = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    AccessibilityNodeInfo next = parent.getParent();
                    parent.recycle();
                    parent = next;
                }
                if (parent != null) parent.recycle();
            }
            node.recycle();
            root.recycle();

            if (!success) return "Элемент найден, но ACTION_CLICK недоступен: " + q;
        }

        afterSuccessfulClick();
        return "CLICK выполнен: " + q;
    }

    public String type(String target, String text) {
        if (stopped) return "STOP активирован.";
        if (clickPaused) return "Действия приостановлены. Нажмите ПРОДОЛЖИТЬ.";

        String q = target == null ? "" : target.trim();
        if (q.isEmpty()) return "TYPE: цель пуста.";

        AccessibilityNodeInfo root = obtainExternalRoot();
        if (root == null) {
            root = lastExternalRoot == null ? null : AccessibilityNodeInfo.obtain(lastExternalRoot);
        }
        if (root == null) return "Экран недоступен.";

        AccessibilityNodeInfo node = find(root, q);
        if (node == null) {
            root.recycle();
            return "Поле не найдено: " + q;
        }

        if (!node.isEditable()) {
            AccessibilityNodeInfo parent = node.getParent();
            if (parent != null) {
                if (parent.isEditable()) {
                    node.recycle();
                    node = parent;
                } else {
                    parent.recycle();
                }
            }
        }

        if (!node.isEditable()) {
            node.recycle();
            root.recycle();
            return "Поле не редактируемое: " + q;
        }

        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text == null ? "" : text);
        boolean ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
        node.recycle();
        root.recycle();
        return ok ? "TYPE выполнен." : "Не удалось установить текст.";
    }

    private boolean clickAt(String value) {
        try {
            String p = value.substring(1);
            String[] parts = p.split(",");
            if (parts.length != 2) return false;
            float x = Float.parseFloat(parts[0].trim());
            float y = Float.parseFloat(parts[1].trim());

            Path path = new Path();
            path.moveTo(x, y);
            GestureDescription.StrokeDescription stroke =
                    new GestureDescription.StrokeDescription(path, 0, 80);
            GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();
            return dispatchGesture(gesture, null, null);
        } catch (Throwable e) {
            return false;
        }
    }

    private void afterSuccessfulClick() {
        clickCount++;
        if (overlay != null) overlay.update();
        if (clickCount >= clickCheckpoint) {
            clickPaused = true;
            if (overlay != null) overlay.showContinuePrompt(clickCount, clickCheckpoint);
        }
    }

    private AccessibilityNodeInfo find(AccessibilityNodeInfo root, String target) {
        if (root == null) return null;
        return findRecursive(root, target.toLowerCase(Locale.ROOT));
    }

    private AccessibilityNodeInfo findRecursive(AccessibilityNodeInfo node, String q) {
        if (node == null) return null;
        if (matches(node, q)) return AccessibilityNodeInfo.obtain(node);

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            AccessibilityNodeInfo hit = findRecursive(child, q);
            child.recycle();
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
        return a.equals(q) || b.equals(q) || v.equals(q) ||
                a.contains(q) || b.contains(q) || v.contains(q);
    }

    private AccessibilityNodeInfo obtainExternalRoot() {
        try {
            List<AccessibilityWindowInfo> windows = getWindows();
            AccessibilityNodeInfo fallback = null;

            for (AccessibilityWindowInfo window : windows) {
                AccessibilityNodeInfo root = window.getRoot();
                if (root == null) continue;
                CharSequence p = root.getPackageName();
                String pkg = p == null ? "" : p.toString();

                if (!pkg.isEmpty() && !pkg.equals(getPackageName()) &&
                        pkg.equals(lastExternalPackage)) {
                    AccessibilityNodeInfo copy = AccessibilityNodeInfo.obtain(root);
                    root.recycle();
                    if (fallback != null) fallback.recycle();
                    return copy;
                }

                if (fallback == null && !pkg.isEmpty() && !pkg.equals(getPackageName())) {
                    fallback = AccessibilityNodeInfo.obtain(root);
                }
                root.recycle();
            }

            return fallback;
        } catch (Throwable e) {
            return null;
        }
    }
}
