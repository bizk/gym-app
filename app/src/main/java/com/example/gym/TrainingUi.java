package com.example.gym;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

/** Shared visual language, with sizes and spacing adapted to a round watch. */
final class TrainingUi {
    static final int BACKGROUND = Color.rgb(12, 15, 14);
    static final int SURFACE = Color.rgb(26, 31, 28);
    static final int TEXT = Color.rgb(244, 247, 241);
    static final int MUTED = Color.rgb(171, 183, 174);
    static final int ACCENT = Color.rgb(211, 250, 118);
    static final int DANGER = Color.rgb(255, 177, 166);
    final Activity activity;
    final boolean watch;
    LinearLayout body;
    private LinearLayout root;
    private ScrollView scroll;
    private FrameLayout host;
    private View overlay;
    private Runnable dismissOverlay;

    TrainingUi(Activity activity, boolean watch) {
        this.activity = activity;
        this.watch = watch;
    }

    int dp(float value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }

    void screen() {
        root = column();
        root.setBackgroundColor(BACKGROUND);
        scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(!watch);
        body = column();
        body.setPadding(dp(24), dp(watch ? 22 : 20), dp(24), dp(watch ? 40 : 24));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        host = new FrameLayout(activity);
        host.addView(root);
        overlay = null;
        dismissOverlay = null;
        activity.setContentView(host);
        // Android 15 draws behind system bars. Keep phone controls inside safe insets.
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (!watch) {
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars()
                            | android.view.WindowInsets.Type.displayCutout());
                    v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                } else v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        root.requestApplyInsets();
        if (watch) {
            scroll.setFocusable(true);
            scroll.requestFocus();
            scroll.setOnGenericMotionListener((v, event) -> {
                if (event.getAction() == android.view.MotionEvent.ACTION_SCROLL) {
                    float delta = event.getAxisValue(android.view.MotionEvent.AXIS_SCROLL);
                    scroll.smoothScrollBy(0, -dp(delta * 48));
                    return delta != 0;
                }
                return false;
            });
        }
    }

    void overlay(Runnable dismissed) {
        LinearLayout previousBody = body;
        root.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        FrameLayout panel = new FrameLayout(activity) {
            private float startX, startY;
            private boolean swiping;
            @Override public boolean onInterceptTouchEvent(android.view.MotionEvent event) {
                if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                    startX = event.getX(); startY = event.getY(); swiping = false;
                } else if (event.getActionMasked() == android.view.MotionEvent.ACTION_MOVE) {
                    float dx = event.getX() - startX, dy = event.getY() - startY;
                    if (dx < -dp(36) && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                        swiping = true;
                        return true;
                    }
                }
                return super.onInterceptTouchEvent(event);
            }
            @Override public boolean onTouchEvent(android.view.MotionEvent event) {
                if (event.getActionMasked() == android.view.MotionEvent.ACTION_UP) {
                    if (swiping) closeOverlay(); else performClick();
                }
                return true;
            }
            @Override public boolean performClick() {
                super.performClick();
                return true;
            }
        };
        panel.setBackgroundColor(BACKGROUND);
        ScrollView content = new ScrollView(activity);
        content.setVerticalScrollBarEnabled(false);
        content.setFocusable(true);
        content.setOnGenericMotionListener((v, event) -> {
            float delta = event.getAxisValue(android.view.MotionEvent.AXIS_SCROLL);
            if (event.getAction() != android.view.MotionEvent.ACTION_SCROLL || delta == 0) return false;
            content.smoothScrollBy(0, -dp(delta * 48)); return true;
        });
        body = column();
        body.setPadding(dp(26), dp(26), dp(26), dp(40));
        content.addView(body);
        panel.addView(content, new FrameLayout.LayoutParams(-1, -1));
        host.addView(panel, new FrameLayout.LayoutParams(-1, -1));
        overlay = panel;
        dismissOverlay = () -> { body = previousBody; dismissed.run(); };
        content.requestFocus();
    }

    boolean closeOverlay() {
        if (overlay == null) return false;
        host.removeView(overlay);
        root.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        overlay = null;
        Runnable callback = dismissOverlay;
        dismissOverlay = null;
        callback.run();
        scroll.requestFocus();
        return true;
    }

    LinearLayout column() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    LinearLayout row() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        return layout;
    }

    TextView label(String value, int size, int color, boolean bold) {
        TextView text = new TextView(activity);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        text.setGravity(watch ? Gravity.CENTER : Gravity.START);
        text.setIncludeFontPadding(false);
        return text;
    }

    TextView text(String value, int size, int color, boolean bold) {
        TextView text = label(value, size, color, bold);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(watch ? 4 : 12);
        body.addView(text, params);
        return text;
    }

    void eyebrow(String value) { text(value, watch ? 10 : 12, ACCENT, true); }
    void title(String value) { text(value, watch ? 20 : 32, TEXT, true); }
    void muted(String value) { text(value, watch ? 13 : 15, MUTED, false); }
    void gap(int height) { body.addView(new View(activity), new LinearLayout.LayoutParams(1, dp(height))); }

    GradientDrawable background(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    LinearLayout card() {
        LinearLayout card = column();
        card.setPadding(dp(20), dp(20), dp(20), dp(20));
        card.setBackground(background(SURFACE, 24));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(16);
        body.addView(card, params);
        return card;
    }

    void cardText(LinearLayout card, String value, int size, int color, boolean bold) {
        TextView text = label(value, size, color, bold);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(10);
        card.addView(text, params);
    }

    void weekButton(LinearLayout row, int number, boolean selected, int[] days, Runnable click) {
        LinearLayout tile = column();
        tile.setPadding(dp(12), dp(14), dp(12), dp(14));
        GradientDrawable surface = background(SURFACE, 16);
        if (selected) surface.setStroke(dp(2), ACCENT);
        tile.setBackground(surface);
        TextView title = label("Semana " + number, 16, selected ? ACCENT : TEXT, true);
        title.setGravity(Gravity.CENTER);
        tile.addView(title, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout segments = row();
        StringBuilder description = new StringBuilder("Semana " + number + (selected ? ", seleccionada" : ""));
        for (int d = 0; d < days.length; d++) {
            View segment = new View(activity);
            int color = days[d] == 2 ? Color.rgb(91, 211, 144)
                    : days[d] == 1 ? Color.rgb(250, 209, 99) : Color.rgb(67, 76, 70);
            segment.setBackground(background(color, 3));
            LinearLayout.LayoutParams part = new LinearLayout.LayoutParams(0, dp(6), 1);
            if (d > 0) part.leftMargin = dp(4);
            segments.addView(segment, part);
            description.append(", día ").append(d + 1).append(days[d] == 2 ? " completo" : days[d] == 1 ? " parcial" : " pendiente");
        }
        LinearLayout.LayoutParams strip = new LinearLayout.LayoutParams(-1, -2);
        strip.topMargin = dp(12);
        tile.addView(segments, strip);
        tile.setContentDescription(description);
        tile.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        title.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        tile.setFocusable(true);
        tile.setOnClickListener(v -> click.run());
        tile.setSelected(selected);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.setMargins(0, 0, dp(8), dp(10));
        row.addView(tile, params);
    }

    Button action(String value, boolean primary, Runnable click) {
        Button button = new Button(activity);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextSize(watch ? 14 : 16);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextColor(primary ? BACKGROUND : TEXT);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(watch ? 48 : 56));
        button.setMinimumHeight(dp(watch ? 48 : 56));
        button.setPadding(dp(12), dp(10), dp(12), dp(10));
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, background(primary ? Color.rgb(180, 216, 96) : Color.rgb(47, 57, 50), 28));
        states.addState(new int[]{}, background(primary ? ACCENT : SURFACE, 28));
        button.setBackground(states);
        button.setOnClickListener(v -> click.run());
        return button;
    }

    void button(String value, boolean primary, Runnable click) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(8);
        body.addView(action(value, primary, click), params);
    }

    void primary(String value, Runnable click) {
        if (watch) { button(value, true, click); return; }
        LinearLayout dock = column();
        dock.setPadding(dp(24), dp(12), dp(24), dp(16));
        dock.setBackgroundColor(BACKGROUND);
        dock.addView(action(value, true, click), new LinearLayout.LayoutParams(-1, -2));
        root.addView(dock);
    }

    void metrics(String reps, String load) {
        LinearLayout row = row();
        if (!watch) {
            row.setPadding(dp(20), dp(20), dp(20), dp(20));
            row.setBackground(background(SURFACE, 24));
        }
        String[] values = {reps, load};
        String[] captions = {"REPS", "CARGA"};
        for (int i = 0; i < 2; i++) {
            LinearLayout cell = column();
            cell.setPadding(dp(4), 0, dp(4), 0);
            TextView value = label(values[i], watch ? 24 : 36, TEXT, true);
            value.setGravity(Gravity.CENTER);
            cell.addView(value, new LinearLayout.LayoutParams(-1, -2));
            TextView caption = label(captions[i], watch ? 10 : 12, MUTED, true);
            caption.setGravity(Gravity.CENTER);
            cell.addView(caption, new LinearLayout.LayoutParams(-1, -2));
            row.addView(cell, new LinearLayout.LayoutParams(0, -2, 1));
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(watch ? 6 : 24);
        body.addView(row, params);
    }

    void watchExercise(String name, String reps, String load, int done, int sets,
            int sessionDone, int sessionTotal, boolean finished, Runnable previous,
            Runnable mark, Runnable next, Runnable undo) {
        body.setPadding(0, 0, 0, dp(40));
        int size = Math.min(activity.getResources().getDisplayMetrics().widthPixels,
                activity.getResources().getDisplayMetrics().heightPixels);
        FrameLayout face = new FrameLayout(activity);
        View rings = new View(activity) {
            private final android.graphics.Paint paint = new android.graphics.Paint(3);
            private final android.graphics.RectF oval = new android.graphics.RectF();
            private final float[] progress = {(float) sessionDone / Math.max(1, sessionTotal),
                    (float) done / Math.max(1, sets)};
            @Override protected void onDraw(android.graphics.Canvas canvas) {
                paint.setStyle(android.graphics.Paint.Style.STROKE);
                paint.setStrokeWidth(dp(4));
                paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
                for (int i = 0; i < 2; i++) {
                    float inset = dp(5 + i * 9);
                    oval.set(inset, inset,
                            getWidth() - inset, getHeight() - inset);
                    paint.setColor(SURFACE);
                    canvas.drawOval(oval, paint);
                    paint.setColor(i == 0 ? ACCENT : Color.rgb(111, 205, 216));
                    canvas.drawArc(oval, -90, 360 * Math.min(1, progress[i]), false, paint);
                }
            }
        };
        rings.setContentDescription("Sesión: " + sessionDone + " de " + sessionTotal
                + " series. Ejercicio: " + done + " de " + sets + " series.");
        face.addView(rings, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout center = column();
        center.setGravity(Gravity.CENTER);
        TextView title = label(name, 14, TEXT, true);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setContentDescription(name);
        center.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView prescription = label(reps + " reps · " + load, 17, TEXT, true);
        center.addView(prescription, new LinearLayout.LayoutParams(-1, -2));
        TextView count = label(finished ? "Ejercicio cerrado · " + done + "/" + sets
                : "Serie " + (done + 1) + " de " + sets, 11, MUTED, false);
        center.addView(count, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout controls = row();
        String[] symbols = {"‹", "✓", "›"};
        String[] descriptions = {"Ejercicio anterior", "Marcar serie realizada", "Ejercicio siguiente"};
        Runnable[] actions = {previous, mark, next};
        for (int i = 0; i < 3; i++) {
            Button button = action(symbols[i], i == 1, actions[i]);
            button.setTextSize(24);
            button.setPadding(0, 0, 0, 0);
            button.setMinWidth(0);
            button.setMinimumWidth(0);
            button.setContentDescription(descriptions[i]);
            if (i == 1 && finished) { button.setEnabled(false); button.setAlpha(.35f); }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1);
            params.setMargins(dp(2), dp(5), dp(2), 0);
            controls.addView(button, params);
        }
        center.addView(controls, new LinearLayout.LayoutParams(-1, -2));
        Button back = action("↶", false, undo);
        back.setTextSize(18);
        back.setPadding(0, 0, 0, 0);
        back.setContentDescription("Deshacer último set");
        back.setEnabled(done > 0);
        back.setAlpha(done > 0 ? 1f : .35f);
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        backParams.gravity = Gravity.CENTER;
        center.addView(back, backParams);
        FrameLayout.LayoutParams content = new FrameLayout.LayoutParams(size - dp(58), -2, Gravity.CENTER);
        face.addView(center, content);
        body.addView(face, new LinearLayout.LayoutParams(-1, size));
        LinearLayout actionsBelow = column();
        actionsBelow.setPadding(dp(28), dp(12), dp(28), 0);
        body.addView(actionsBelow, new LinearLayout.LayoutParams(-1, -2));
        body = actionsBelow;
    }

    void progress(int done, int total) {
        ProgressBar bar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(Math.max(1, total));
        bar.setProgress(done);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(SURFACE));
        bar.setContentDescription(done + " de " + total + " series completadas");
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(6));
        params.bottomMargin = dp(watch ? 8 : 20);
        body.addView(bar, params);
    }
}
