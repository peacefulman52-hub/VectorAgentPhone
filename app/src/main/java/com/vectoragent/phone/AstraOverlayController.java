package com.vectoragent.phone;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.InputMethodManager;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

final class AstraOverlayController {
    private final AstraAccessibilityService service;
    private final WindowManager wm;
    private final int density;

    private FrameLayout root;
    private LinearLayout bar;
    private TextView info;
    private boolean attached;
    private boolean panelMode;

    AstraOverlayController(AstraAccessibilityService service) {
        this.service = service;
        this.wm = (WindowManager) service.getSystemService(Context.WINDOW_SERVICE);
        this.density = (int) (service.getResources().getDisplayMetrics().density + 0.5f);
        buildBar();
    }

    private int dp(int v) {
        return (int) (v * service.getResources().getDisplayMetrics().density + 0.5f);
    }

    private Button button(String text) {
        Button b = new Button(service);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setPadding(dp(4), 0, dp(4), 0);
        return b;
    }

    private TextView label(String text, float size) {
        TextView t = new TextView(service);
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(Color.WHITE);
        t.setPadding(dp(8), dp(4), dp(8), dp(4));
        return t;
    }

    private void buildBar() {
        root = new FrameLayout(service);
        root.setBackgroundColor(Color.TRANSPARENT);

        bar = new LinearLayout(service);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(4), dp(3), dp(4), dp(3));
        bar.setBackgroundColor(Color.argb(230, 25, 25, 30));
        bar.setElevation(dp(6));

        Button read = button("READ");
        Button click = button("CLICK");
        Button stop = button("STOP");
        Button resume = button("RESUME");
        Button limit = button("N=10");
        info = label("0/10", 11);
        info.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        bar.addView(read, new LinearLayout.LayoutParams(0, dp(42), 1));
        bar.addView(click, new LinearLayout.LayoutParams(0, dp(42), 1));
        bar.addView(stop, new LinearLayout.LayoutParams(0, dp(42), 1));
        bar.addView(resume, new LinearLayout.LayoutParams(0, dp(42), 1));
        bar.addView(limit, new LinearLayout.LayoutParams(0, dp(42), 0.8f));
        bar.addView(info, new LinearLayout.LayoutParams(dp(58), dp(42)));

        root.addView(bar, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        read.setOnClickListener(v -> {
            String s = service.readScreen();
            showResult("READ:\n" + compact(s, 900));
        });

        click.setOnClickListener(v -> showClickChooser());

        stop.setOnClickListener(v -> {
            service.stopExecution();
            showResult("STOP активирован.");
        });

        resume.setOnClickListener(v -> {
            service.resumeExecution();
            showResult("Продолжение разрешено.");
        });

        limit.setOnClickListener(v -> showLimitPanel());
    }

    private void showResult(String text) {
        info.setText(text.replace("\n", " | ").trim());
        info.postDelayed(this::update, 4500);
    }

    private String compact(String s, int max) {
        if (s == null) return "";
        String x = s.replace('\n', ' ').replaceAll("\\s+", " ").trim();
        return x.length() <= max ? x : x.substring(0, max) + "…";
    }

    private void showClickChooser() {
        panelMode = true;
        removeCurrent();

        LinearLayout panel = new LinearLayout(service);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(8), dp(8), dp(8), dp(8));
        panel.setBackgroundColor(Color.argb(245, 25, 25, 30));

        TextView title = label("Куда кликнуть? Выбери элемент текущей страницы:", 14);
        panel.addView(title);

        ScrollView scroll = new ScrollView(service);
        LinearLayout list = new LinearLayout(service);
        list.setOrientation(LinearLayout.VERTICAL);

        List<AstraAccessibilityService.ClickTarget> targets = service.clickableTargets(25);
        if (targets.isEmpty()) {
            list.addView(label("На странице нет доступных CLICK-целей.", 13));
        } else {
            for (AstraAccessibilityService.ClickTarget t : targets) {
                Button b = button(t.label);
                b.setTextSize(13);
                b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                b.setOnClickListener(v -> {
                    String out = service.click(t.target);
                    backToBar();
                    showResult(out);
                });
                list.addView(b, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(46)));
            }
        }
        scroll.addView(list);

        panel.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(360)));

        Button back = button("← НАЗАД");
        back.setOnClickListener(v -> backToBar());
        panel.addView(back);

        root.addView(panel, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        setWindowFocusable(false);
    }

    private void showLimitPanel() {
        panelMode = true;
        removeCurrent();

        LinearLayout panel = new LinearLayout(service);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(10), dp(12), dp(10));
        panel.setBackgroundColor(Color.argb(250, 25, 25, 30));

        panel.addView(label("Пауза после скольких кликов?", 15));

        EditText input = new EditText(service);
        input.setSingleLine(true);
        input.setText(String.valueOf(service.getClickCheckpoint()));
        input.setHint("Пусто = 10");
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.LTGRAY);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        panel.addView(input);

        LinearLayout buttons = new LinearLayout(service);
        Button ok = button("OK");
        Button cancel = button("ОТМЕНА");
        buttons.addView(ok, new LinearLayout.LayoutParams(0, dp(46), 1));
        buttons.addView(cancel, new LinearLayout.LayoutParams(0, dp(46), 1));
        panel.addView(buttons);

        ok.setOnClickListener(v -> {
            String raw = input.getText().toString().trim();
            int n = 10;
            if (!raw.isEmpty()) {
                try { n = Integer.parseInt(raw); } catch (Throwable ignored) {}
            }
            if (n <= 0) n = 10;
            service.setClickCheckpoint(n);
            hideKeyboard(input);
            backToBar();
        });
        cancel.setOnClickListener(v -> {
            hideKeyboard(input);
            backToBar();
        });

        root.addView(panel, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        setWindowFocusable(true);
        input.requestFocus();
        input.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) service.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }, 120);
    }

    void showContinuePrompt(int count, int checkpoint) {
        panelMode = true;
        removeCurrent();

        LinearLayout panel = new LinearLayout(service);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(10), dp(12), dp(10));
        panel.setBackgroundColor(Color.argb(250, 25, 25, 30));

        panel.addView(label("Выполнено " + count + " кликов.", 15));
        panel.addView(label("Продолжить ещё " + checkpoint + "?", 14));

        LinearLayout buttons = new LinearLayout(service);
        Button cont = button("▶ ПРОДОЛЖИТЬ");
        Button stop = button("🛑 STOP");
        buttons.addView(cont, new LinearLayout.LayoutParams(0, dp(48), 1));
        buttons.addView(stop, new LinearLayout.LayoutParams(0, dp(48), 1));
        panel.addView(buttons);

        cont.setOnClickListener(v -> {
            service.continueClickBatch();
            backToBar();
        });
        stop.setOnClickListener(v -> {
            service.stopExecution();
            backToBar();
            showResult("STOP активирован.");
        });

        root.addView(panel, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        setWindowFocusable(false);
    }

    void show() {
        if (attached || wm == null) {
            update();
            return;
        }

        WindowManager.LayoutParams p = params();
        try {
            wm.addView(root, p);
            attached = true;
        } catch (Throwable ignored) {
            attached = false;
        }
        update();
    }

    void hide() {
        if (!attached || wm == null) return;
        try { wm.removeView(root); } catch (Throwable ignored) {}
        attached = false;
    }

    void destroy() {
        hide();
    }

    void update() {
        if (info == null) return;
        int n = service.getClickCheckpoint();
        if (service.isStopped()) info.setText("STOP");
        else if (service.isClickPaused()) info.setText(service.getClickCount() + "/" + n + " • PAUSE");
        else info.setText(service.getClickCount() + "/" + n);
    }

    private void removeCurrent() {
        if (root != null) root.removeAllViews();
    }

    private void backToBar() {
        hideKeyboard(null);
        panelMode = false;
        removeCurrent();
        root.addView(bar, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        setWindowFocusable(false);
        update();
    }

    private void hideKeyboard(View v) {
        try {
            InputMethodManager imm = (InputMethodManager) service.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && v != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        } catch (Throwable ignored) {}
    }

    private WindowManager.LayoutParams params() {
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                flags,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        p.y = dp(22);
        return p;
    }

    private void setWindowFocusable(boolean focusable) {
        if (!attached) return;
        WindowManager.LayoutParams p = params();
        if (focusable) {
            p.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        }
        try { wm.updateViewLayout(root, p); } catch (Throwable ignored) {}
    }
}
