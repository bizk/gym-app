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
    static final int ACCENT = Color.rgb(255, 138, 31);
    static final int ACCENT_PRESSED = Color.rgb(217, 112, 15);
    /** Completed work: dark orange with black marks, 4.9:1 contrast. */
    static final int DONE = Color.rgb(200, 90, 0);
    static final int DONE_INK = Color.BLACK;
    static final int DANGER = Color.rgb(255, 177, 166);
    /** Marks a finished week or day in the watch pickers. */
    static final int GREEN = Color.rgb(123, 218, 133);
    final Activity activity;
    final boolean watch;
    LinearLayout body;
    private LinearLayout root;
    private SwipeScroll scroll;
    private FrameLayout host, pages;
    private View overlay;
    /** Low-power summary shown while the watch is in ambient mode; null when interactive. */
    private String ambientText;
    private TextView ambientView;
    private Runnable dismissOverlay;

    TrainingUi(Activity activity, boolean watch) {
        this.activity = activity;
        this.watch = watch;
    }

    int dp(float value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }

    void screen() {
        root = column();
        root.setBackgroundColor(BACKGROUND);
        scroll = new SwipeScroll();
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(!watch);
        body = column();
        body.setPadding(dp(24), dp(watch ? 22 : 20), dp(24), dp(watch ? 40 : 24));
        scroll.addView(body);
        pages = new FrameLayout(activity);
        pages.setClipChildren(true);
        pages.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        root.addView(pages, new LinearLayout.LayoutParams(-1, 0, 1));
        host = new FrameLayout(activity);
        host.addView(root);
        overlay = null;
        dismissOverlay = null;
        activity.setContentView(host);
        ambientView = null;
        if (ambientText != null) ambient(ambientText);
        // Android 15 draws behind system bars. Keep phone controls inside safe insets.
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (!watch) {
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    // Include the keyboard so focused fields and the bottom action stay visible.
                    android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars()
                            | android.view.WindowInsets.Type.displayCutout() | android.view.WindowInsets.Type.ime());
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
                    // Each rotary step moves one fixed page.
                    if (delta != 0) scroll.page(delta < 0 ? 1 : -1);
                    return delta != 0;
                }
                return false;
            });
        }
    }

    /** Covers the screen with dim gray text on black, or removes the cover when {@code text} is null. */
    void ambient(String text) {
        ambientText = text;
        if (text == null) {
            if (ambientView != null) host.removeView(ambientView);
            ambientView = null;
            return;
        }
        if (ambientView == null) {
            ambientView = new TextView(activity);
            ambientView.setBackgroundColor(Color.BLACK);
            ambientView.setTextColor(Color.rgb(150, 150, 150));
            ambientView.setTextSize(15);
            ambientView.setGravity(Gravity.CENTER);
            ambientView.setLineSpacing(dp(4), 1);
            ambientView.setPadding(dp(32), dp(32), dp(32), dp(32));
            ambientView.getPaint().setAntiAlias(false);
            host.addView(ambientView, new FrameLayout.LayoutParams(-1, -1));
        }
        ambientView.setText(text);
    }

    /** Watch: dragging the screen to the right goes back to the previous screen. */
    void swipeBack(Runnable back) { if (watch) scroll.backward = back; }

    /** Builds the screen revealed while dragging right, so going back shows where it leads. */
    void swipeBackPreview(Runnable build) { scroll.buildPrevious = build; }

    /** Dragging left reveals {@code preview} and settles on {@code forward}. */
    void swipeForward(Runnable preview, Runnable forward) {
        scroll.buildNext = preview;
        scroll.forward = forward;
    }

    void overlay(Runnable dismissed) {
        LinearLayout previousBody = body;
        root.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        FrameLayout panel = new FrameLayout(activity) {
            private final int slop = android.view.ViewConfiguration.get(activity).getScaledTouchSlop();
            private float startX, startY, lastX;
            private long lastTime;
            private float speed;
            private boolean swiping;
            @Override public boolean onInterceptTouchEvent(android.view.MotionEvent event) {
                if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                    startX = lastX = event.getRawX(); startY = event.getRawY(); swiping = false; speed = 0;
                    lastTime = event.getEventTime();
                } else if (event.getActionMasked() == android.view.MotionEvent.ACTION_MOVE) {
                    float dx = event.getRawX() - startX, dy = event.getRawY() - startY;
                    if (dx > slop && dx > Math.abs(dy)) {
                        swiping = true;
                        return true;
                    }
                }
                return super.onInterceptTouchEvent(event);
            }
            @Override public boolean onTouchEvent(android.view.MotionEvent event) {
                if (!swiping) {
                    if (event.getActionMasked() == android.view.MotionEvent.ACTION_UP) performClick();
                    return true;
                }
                float dx = Math.max(0, event.getRawX() - startX);
                switch (event.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_MOVE:
                        long elapsed = Math.max(1, event.getEventTime() - lastTime);
                        speed = (event.getRawX() - lastX) * 1000f / elapsed;
                        lastX = event.getRawX(); lastTime = event.getEventTime();
                        // The panel follows the finger, uncovering the exercise beneath it.
                        setTranslationX(dx);
                        break;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        swiping = false;
                        boolean close = event.getActionMasked() == android.view.MotionEvent.ACTION_UP
                                && (dx > getWidth() * .25f || speed > dp(500));
                        float end = close ? getWidth() : 0;
                        animate().translationX(end)
                                .setDuration((long) Math.max(60, 160 * Math.abs(end - dx) / Math.max(1, getWidth())))
                                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                                .withEndAction(() -> { if (close) closeOverlay(); }).start();
                        break;
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
        states.addState(new int[]{android.R.attr.state_pressed}, background(primary ? ACCENT_PRESSED : Color.rgb(47, 57, 50), 28));
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

    void metrics(String reps, String load) { metrics(reps, load, null); }

    /** Reps and load card; a pencil edits both in place when {@code edit} is given. */
    void metrics(String reps, String load, Runnable edit) {
        LinearLayout row = row();
        if (!watch) {
            row.setPadding(dp(20), dp(20), dp(edit == null ? 20 : 8), dp(20));
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
        if (edit != null) {
            ImageButton pencil = iconButton(R.drawable.ic_edit, "Editar repeticiones y carga", ACCENT, edit);
            pencil.setBackground(null);
            row.addView(pencil, new LinearLayout.LayoutParams(dp(48), dp(48)));
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(watch ? 6 : 16);
        body.addView(row, params);
    }

    android.graphics.drawable.Drawable icon(int res, int color) {
        android.graphics.drawable.Drawable drawable = activity.getDrawable(res).mutate();
        drawable.setTint(color);
        return drawable;
    }

    /** Square icon button; the vector is centered exactly, unlike a text glyph. */
    ImageButton iconButton(int res, String description, int color, Runnable click) {
        ImageButton button = new ImageButton(activity);
        button.setImageDrawable(icon(res, color));
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setPadding(0, 0, 0, 0);
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, background(Color.rgb(47, 57, 50), 24));
        states.addState(new int[]{}, background(SURFACE, 24));
        button.setBackground(states);
        button.setContentDescription(description);
        button.setOnClickListener(v -> click.run());
        return button;
    }

    /** Button with an icon before its label, both centered. */
    LinearLayout iconAction(int res, String value, Runnable click) {
        LinearLayout button = row();
        button.setGravity(Gravity.CENTER);
        button.setMinimumHeight(dp(56));
        button.setPadding(dp(12), dp(10), dp(12), dp(10));
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, background(Color.rgb(47, 57, 50), 28));
        states.addState(new int[]{}, background(SURFACE, 28));
        button.setBackground(states);
        ImageView image = new ImageView(activity);
        image.setImageDrawable(icon(res, ACCENT));
        button.addView(image, new LinearLayout.LayoutParams(dp(22), dp(22)));
        TextView text = label(value, 16, TEXT, false);
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        text.setPadding(dp(10), 0, 0, 0);
        button.addView(text);
        button.setClickable(true);
        button.setFocusable(true);
        button.setContentDescription(value);
        button.setOnClickListener(v -> click.run());
        return button;
    }

    /** Two equal buttons side by side. */
    void pair(View first, View second) {
        LinearLayout row = row();
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, -2, 1);
        left.rightMargin = dp(4);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, -2, 1);
        right.leftMargin = dp(4);
        row.addView(first, left);
        row.addView(second, right);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(8);
        body.addView(row, params);
    }

    /** Completed items: dark orange fill with black text. */
    void markDone(Button button) {
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, background(Color.rgb(160, 72, 0), 28));
        states.addState(new int[]{}, background(DONE, 28));
        button.setBackground(states);
        button.setTextColor(DONE_INK);
    }

    void doneButton(String value, Runnable click) {
        Button button = action(value, false, click);
        markDone(button);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(8);
        body.addView(button, params);
    }

    /** Labelled text field in the app's surface style; {@code step} adds − / + buttons for numeric values. */
    EditText field(String caption, String value, String hint, int inputType, double step) {
        TextView label = label(caption, watch ? 10 : 12, MUTED, true);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2);
        labelParams.bottomMargin = dp(8);
        body.addView(label, labelParams);
        LinearLayout row = row();
        EditText input = new EditText(activity);
        input.setText(value);
        input.setHint(hint);
        input.setHintTextColor(Color.rgb(98, 108, 101));
        input.setTextColor(TEXT);
        input.setTextSize(watch ? 16 : 22);
        input.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        input.setInputType(inputType);
        input.setSingleLine(true);
        input.setGravity(step > 0 ? Gravity.CENTER : Gravity.START | Gravity.CENTER_VERTICAL);
        input.setMinHeight(dp(watch ? 48 : 60));
        input.setPadding(dp(16), dp(12), dp(16), dp(12));
        input.setContentDescription(caption);
        input.setSelectAllOnFocus(true);
        GradientDrawable idle = background(SURFACE, 18), focused = background(SURFACE, 18);
        focused.setStroke(dp(2), ACCENT);
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{}, idle);
        input.setBackground(states);
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            GradientDrawable cursor = background(ACCENT, 1);
            cursor.setSize(dp(2), dp(24));
            input.setTextCursorDrawable(cursor);
        }
        if (step > 0) row.addView(stepper(R.drawable.ic_remove, "Restar a " + caption, input, -step), new LinearLayout.LayoutParams(dp(60), dp(60)));
        LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(0, -2, 1);
        if (step > 0) { fieldParams.leftMargin = dp(8); fieldParams.rightMargin = dp(8); }
        row.addView(input, fieldParams);
        if (step > 0) row.addView(stepper(R.drawable.ic_add, "Sumar a " + caption, input, step), new LinearLayout.LayoutParams(dp(60), dp(60)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(20);
        body.addView(row, params);
        return input;
    }

    /** Adjusts a leading number ("20 kg" → "22.5 kg"); values like "8-10" are left untouched. */
    private ImageButton stepper(int res, String description, EditText input, double delta) {
        return iconButton(res, description, ACCENT, () -> {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\s*(\\d+(?:[.,]\\d+)?)(\\s*\\D*)$")
                    .matcher(input.getText().toString());
            double number = 0;
            String suffix = "";
            if (m.matches()) { number = Double.parseDouble(m.group(1).replace(',', '.')); suffix = m.group(2); }
            else if (!input.getText().toString().trim().isEmpty()) return;
            number = Math.max(0, number + delta);
            String text = number == Math.rint(number) ? String.valueOf((long) number) : String.valueOf(number);
            input.setText(text + suffix);
            input.setSelection(input.getText().length());
        });
    }

    /** Flexible space that pushes content apart on screens shorter than the viewport. */
    void flex() { body.addView(new View(activity), new LinearLayout.LayoutParams(1, 0, 1)); }

    /** Centered "‹ title ›" selector; arrows hide at the ends. Caption and detail are optional. */
    void switcher(String caption, int captionColor, String title, boolean large, String detail, int detailColor,
            String what, Runnable previous, Runnable next) {
        if (caption != null) {
            TextView eyebrow = label(caption, 12, captionColor, true);
            eyebrow.setGravity(Gravity.CENTER);
            eyebrow.setLetterSpacing(.12f);
            body.addView(eyebrow, new LinearLayout.LayoutParams(-1, -2));
        }
        int arrow = large ? 56 : 44;
        LinearLayout row = row();
        row.setGravity(Gravity.CENTER);
        View left = previous == null ? new View(activity) : iconButton(R.drawable.ic_chevron_left, what + " anterior", large ? TEXT : MUTED, previous);
        View right = next == null ? new View(activity) : iconButton(R.drawable.ic_chevron_right, what + " siguiente", large ? TEXT : MUTED, next);
        if (previous != null) left.setBackground(null);
        if (next != null) right.setBackground(null);
        row.addView(left, new LinearLayout.LayoutParams(dp(arrow), dp(arrow)));
        TextView value = label(title, large ? 34 : 16, large ? TEXT : TEXT, true);
        value.setGravity(Gravity.CENTER);
        value.setMaxLines(2);
        value.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        if (large) row.addView(value, new LinearLayout.LayoutParams(0, -2, 1));
        else {
            value.setPadding(dp(12), 0, dp(12), 0);
            value.setMinWidth(dp(120));
            row.addView(value, new LinearLayout.LayoutParams(-2, -2));
        }
        row.addView(right, new LinearLayout.LayoutParams(dp(arrow), dp(arrow)));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.topMargin = dp(caption == null ? 0 : 4);
        body.addView(row, rowParams);
        if (detail != null) {
            TextView sub = label(detail, 14, detailColor, detailColor != MUTED);
            sub.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1, -2);
            subParams.topMargin = dp(2);
            body.addView(sub, subParams);
        }
    }

    /** Thin progress strip, one segment per entry: orange done, dim orange started, gray untouched. */
    void segments(int[] status, int selected) {
        LinearLayout strip = row();
        for (int i = 0; i < status.length; i++) {
            View segment = new View(activity);
            int color = status[i] == 2 ? ACCENT : status[i] == 1 ? Color.argb(110, 255, 138, 31) : Color.rgb(47, 57, 50);
            segment.setBackground(background(color, 3));
            LinearLayout.LayoutParams part = new LinearLayout.LayoutParams(0, dp(i == selected ? 8 : 5), 1);
            if (i > 0) part.leftMargin = dp(5);
            strip.addView(segment, part);
        }
        strip.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(8));
        params.setMargins(dp(56), dp(14), dp(56), 0);
        body.addView(strip, params);
    }

    /** Top row with a centered tappable caption and an icon on the right. */
    void header(String caption, Runnable pick, int icon, String iconDescription, Runnable iconClick) {
        LinearLayout bar = row();
        bar.addView(new View(activity), new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView picker = label(caption, 14, ACCENT, true);
        picker.setGravity(Gravity.CENTER);
        picker.setMinHeight(dp(48));
        picker.setMaxLines(1);
        picker.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if (pick != null) {
            picker.setText(caption + "  ▾");
            picker.setOnClickListener(v -> pick.run());
            picker.setContentDescription(caption + ", cambiar plan");
        }
        bar.addView(picker, new LinearLayout.LayoutParams(0, -2, 1));
        ImageButton action = iconButton(icon, iconDescription, TEXT, iconClick);
        action.setBackground(null);
        bar.addView(action, new LinearLayout.LayoutParams(dp(48), dp(48)));
        body.addView(bar, new LinearLayout.LayoutParams(-1, -2));
    }

    /** Back arrow in the top-left corner, optionally followed by a small caption. */
    void topBar(String caption, Runnable back) { topBar(caption, back, null); }

    /** Same bar with a gear in the top-right corner that opens {@code settings}. */
    void topBar(String caption, Runnable back, Runnable settings) {
        LinearLayout bar = row();
        ImageButton arrow = iconButton(R.drawable.ic_back, "Atrás", TEXT, back);
        bar.addView(arrow, new LinearLayout.LayoutParams(dp(48), dp(48)));
        if (caption != null) {
            TextView text = label(caption, 12, ACCENT, true);
            text.setGravity(Gravity.CENTER);
            text.setPadding(dp(8), 0, dp(8), 0);
            text.setMaxLines(2);
            bar.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        } else bar.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1));
        // A matching slot on the right keeps the caption centered on screen.
        if (settings != null) bar.addView(iconButton(R.drawable.ic_settings, "Más opciones", TEXT, settings),
                new LinearLayout.LayoutParams(dp(48), dp(48)));
        else bar.addView(new View(activity), new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(16);
        body.addView(bar, params);
    }

    /** Sets as one row of segments that fill with color as they are completed. */
    void setRow(int done, int sets, boolean finished, int animate, Runnable undo) {
        LinearLayout row = row();
        int height = sets > 10 ? 36 : 48;
        for (int i = 0; i < sets; i++) {
            boolean complete = i < done, now = !finished && i == done;
            TextView cell = label(complete ? "✓" : String.valueOf(i + 1), sets > 10 ? 12 : 16,
                    complete ? DONE_INK : now ? ACCENT : MUTED, true);
            cell.setGravity(Gravity.CENTER);
            GradientDrawable fill = background(complete ? ACCENT : SURFACE, 14);
            if (now) fill.setStroke(dp(2), ACCENT);
            cell.setBackground(fill);
            cell.setContentDescription("Serie " + (i + 1) + (complete ? ", lista" : now ? ", ahora" : finished ? ", sin realizar" : ", pendiente"));
            LinearLayout.LayoutParams part = new LinearLayout.LayoutParams(0, dp(height), 1);
            if (i > 0) part.leftMargin = dp(sets > 10 ? 3 : 6);
            row.addView(cell, part);
            if (i == animate) {
                cell.setScaleX(.6f); cell.setScaleY(.6f); cell.setAlpha(.4f);
                cell.animate().scaleX(1).scaleY(1).alpha(1).setDuration(260)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(2.2f)).start();
            }
        }
        if (undo != null) {
            ImageButton back = iconButton(R.drawable.ic_undo, "Deshacer última serie", TEXT, undo);
            back.setBackground(background(SURFACE, 14));
            back.setEnabled(done > 0);
            back.setAlpha(done > 0 ? 1f : .3f);
            LinearLayout.LayoutParams part = new LinearLayout.LayoutParams(dp(height), dp(height));
            part.leftMargin = dp(sets > 10 ? 6 : 10);
            row.addView(back, part);
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(16);
        body.addView(row, params);
    }

    /** One exercise in a history card: dark orange with black marks when completed. */
    void exerciseChip(LinearLayout card, String name, int done, int sets, boolean complete) {
        TextView chip = label((complete ? "✓  " : "") + name + "  ·  " + done + "/" + sets,
                watch ? 12 : 14, complete ? DONE_INK : MUTED, complete);
        chip.setPadding(dp(12), dp(8), dp(12), dp(8));
        GradientDrawable fill = background(complete ? DONE : BACKGROUND, 12);
        if (!complete) fill.setStroke(dp(1), Color.rgb(47, 57, 50));
        chip.setBackground(fill);
        chip.setContentDescription(name + (complete ? ", completado" : ", " + done + " de " + sets + " series"));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(6);
        (card == null ? body : card).addView(chip, params);
    }

    /**
     * Horizontal paging on the current screen. {@code previous} and {@code next} build preview pages
     * (into {@link #body}) that follow the finger; the callbacks run once a page has settled.
     */
    void pager(Runnable previous, Runnable next, Runnable backward, Runnable forward) {
        scroll.buildPrevious = previous;
        scroll.buildNext = next;
        scroll.backward = backward;
        scroll.forward = forward;
    }

    private View previewPage(Runnable build) {
        LinearLayout saved = body;
        body = column();
        body.setPadding(saved.getPaddingLeft(), saved.getPaddingTop(), saved.getPaddingRight(), saved.getPaddingBottom());
        build.run();
        View page = body;
        body = saved;
        page.setBackgroundColor(BACKGROUND);
        page.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        page.setVisibility(View.INVISIBLE);
        pages.addView(page, 0, new FrameLayout.LayoutParams(-1, -1));
        return page;
    }

    /** Short slide for exercise changes triggered by a button rather than a drag. */
    void slideIn(int direction) {
        if (direction == 0) return;
        float width = activity.getResources().getDisplayMetrics().widthPixels;
        scroll.setTranslationX(direction * width * .2f);
        scroll.setAlpha(.4f);
        scroll.animate().translationX(0).alpha(1).setDuration(140)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    }

    private final class SwipeScroll extends ScrollView {
        View previous, next;
        Runnable buildPrevious, buildNext;
        Runnable forward, backward;
        private final int slop = android.view.ViewConfiguration.get(activity).getScaledTouchSlop();
        private float startX, startY;
        private boolean swiping, settling, flung;
        /** Scroll position when the touch began and the page last aimed at, for watch paging. */
        private int startScroll, target = -1;
        private android.view.VelocityTracker velocity;

        SwipeScroll() { super(activity); }

        private boolean enabled() { return forward != null || backward != null; }

        @Override public boolean onInterceptTouchEvent(android.view.MotionEvent event) {
            if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) { startScroll = getScrollY(); target = -1; }
            if (!enabled() || settling) return settling || super.onInterceptTouchEvent(event);
            if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN && velocity != null) velocity.clear();
            track(event);
            if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                startX = event.getRawX(); startY = event.getRawY(); swiping = false;
            } else if (event.getActionMasked() == android.view.MotionEvent.ACTION_MOVE) {
                float dx = event.getRawX() - startX, dy = event.getRawY() - startY;
                // Claim the gesture as soon as it is clearly horizontal, before buttons or scrolling take it.
                if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) && (forward != null || dx > 0)) {
                    swiping = true;
                    if (previous == null && buildPrevious != null) previous = previewPage(buildPrevious);
                    if (next == null && buildNext != null) next = previewPage(buildNext);
                    getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                }
            }
            return super.onInterceptTouchEvent(event);
        }

        private void track(android.view.MotionEvent event) {
            if (velocity == null) velocity = android.view.VelocityTracker.obtain();
            // Screen coordinates: this view moves under the finger, so local ones would feed back.
            android.view.MotionEvent screen = android.view.MotionEvent.obtain(event);
            screen.setLocation(event.getRawX(), event.getRawY());
            velocity.addMovement(screen);
            screen.recycle();
        }

        private void place(float dx) {
            float width = getWidth();
            setTranslationX(dx);
            if (previous != null) { previous.setVisibility(dx > 0 ? View.VISIBLE : View.INVISIBLE); previous.setTranslationX(dx - width); }
            if (next != null) { next.setVisibility(dx < 0 ? View.VISIBLE : View.INVISIBLE); next.setTranslationX(dx + width); }
        }

        @Override public boolean onTouchEvent(android.view.MotionEvent event) {
            if (settling) return true;
            if (!swiping) {
                int action = event.getActionMasked();
                if (!watch || (action != android.view.MotionEvent.ACTION_UP && action != android.view.MotionEvent.ACTION_CANCEL))
                    return super.onTouchEvent(event);
                flung = false;
                boolean handled = super.onTouchEvent(event);
                // A drag past a fifth of the screen turns the page even without a fling.
                int moved = getScrollY() - startScroll;
                if (!flung) snap(Math.abs(moved) > getHeight() / 5 ? moved : 0);
                return handled;
            }
            track(event);
            float dx = event.getRawX() - startX;
            // Resist dragging toward a side that has no page.
            if ((dx < 0 && forward == null) || (dx > 0 && backward == null)) dx *= .25f;
            switch (event.getActionMasked()) {
                case android.view.MotionEvent.ACTION_MOVE:
                    place(dx);
                    return true;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    swiping = false;
                    velocity.computeCurrentVelocity(1000);
                    float speed = velocity.getXVelocity();
                    velocity.recycle(); velocity = null;
                    boolean fling = Math.abs(speed) > dp(500) && Math.signum(speed) == Math.signum(dx);
                    boolean far = Math.abs(dx) > getWidth() * .25f;
                    Runnable target = event.getActionMasked() == android.view.MotionEvent.ACTION_UP && (fling || far)
                            ? (dx < 0 ? forward : backward) : null;
                    settle(target == null ? 0 : Math.signum(dx) * getWidth(), target);
                    return true;
            }
            return true;
        }

        /** Watch scrolling rests on whole screens; a fling moves exactly one. */
        @Override public void fling(int velocityY) {
            if (!watch) { super.fling(velocityY); return; }
            flung = true;
            snap(velocityY);
        }

        private void snap(int velocityY) {
            int height = getHeight();
            if (height == 0) return;
            int page = velocityY == 0 ? Math.round((float) getScrollY() / height)
                    : Math.round((float) startScroll / height) + (velocityY > 0 ? 1 : -1);
            scrollToPage(page);
        }

        void page(int delta) {
            int height = getHeight();
            if (height == 0) return;
            scrollToPage(Math.round((float) (target >= 0 ? target : getScrollY()) / height) + delta);
        }

        private void scrollToPage(int page) {
            int height = getHeight();
            int max = getChildCount() == 0 ? 0 : Math.max(0, getChildAt(0).getHeight() - height);
            target = Math.max(0, Math.min(max, page * height));
            smoothScrollTo(0, target);
        }

        private void settle(float end, Runnable done) {
            settling = true;
            float start = getTranslationX();
            android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofFloat(start, end);
            animator.setDuration((long) Math.max(60, 160 * Math.abs(end - start) / Math.max(1, getWidth())));
            animator.setInterpolator(new android.view.animation.DecelerateInterpolator());
            animator.addUpdateListener(a -> place((float) a.getAnimatedValue()));
            animator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator a) {
                    settling = false;
                    if (done != null) done.run();
                }
            });
            animator.start();
        }
    }

    void watchExercise(String name, String reps, String load, int done, int sets,
            int sessionDone, int sessionTotal, boolean finished, Runnable previous,
            Runnable mark, Runnable next, Runnable undo,
            String restLabel, Runnable rest, Runnable close, Runnable edit, Runnable options) {
        body.setPadding(0, 0, 0, 0);
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
            params.setMargins(dp(2), dp(8), dp(2), 0);
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
        backParams.topMargin = dp(4);
        center.addView(back, backParams);
        FrameLayout.LayoutParams content = new FrameLayout.LayoutParams(size - dp(58), -2, Gravity.CENTER);
        face.addView(center, content);
        body.addView(face, new LinearLayout.LayoutParams(-1, size));
        // Second fixed page: four actions in a 2 x 2 grid that fits inside the round screen.
        LinearLayout grid = column();
        grid.setGravity(Gravity.CENTER);
        int[] icons = {R.drawable.ic_timer, R.drawable.ic_check, R.drawable.ic_edit, R.drawable.ic_settings};
        String[] captions = {restLabel, "Terminar", "Editar", "Opciones"};
        String[] spoken = {"Descansar " + restLabel, "Terminar ejercicio", "Editar reps y carga", "Más opciones"};
        Runnable[] clicks = {rest, close, edit, options};
        for (int r = 0; r < 2; r++) {
            LinearLayout line = row();
            line.setGravity(Gravity.CENTER);
            for (int c = 0; c < 2; c++) {
                int i = r * 2 + c;
                View tile = tile(icons[i], captions[i], spoken[i], clicks[i]);
                if (i == 1 && finished) { tile.setEnabled(false); tile.setAlpha(.35f); }
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(72), dp(72));
                params.setMargins(dp(4), dp(4), dp(4), dp(4));
                line.addView(tile, params);
            }
            grid.addView(line, new LinearLayout.LayoutParams(-2, -2));
        }
        body.addView(grid, new LinearLayout.LayoutParams(-1, size));
    }

    /**
     * Round watch home: a thin ring of session progress at the edge, the week as a small spaced
     * label, the day in light display type, and one round action with a quieter history control.
     */
    void watchHome(String caption, String day, String detail, float progress,
            String actionDescription, Runnable action, Runnable history) {
        body.setPadding(0, 0, 0, 0);
        int size = Math.min(activity.getResources().getDisplayMetrics().widthPixels,
                activity.getResources().getDisplayMetrics().heightPixels);
        FrameLayout face = new FrameLayout(activity);
        View ring = new View(activity) {
            private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            private final android.graphics.RectF oval = new android.graphics.RectF();
            @Override protected void onDraw(android.graphics.Canvas canvas) {
                paint.setStyle(android.graphics.Paint.Style.STROKE);
                paint.setStrokeWidth(dp(3));
                paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
                float inset = dp(6);
                oval.set(inset, inset, getWidth() - inset, getHeight() - inset);
                paint.setColor(SURFACE);
                canvas.drawOval(oval, paint);
                if (progress > 0) {
                    paint.setColor(ACCENT);
                    canvas.drawArc(oval, -90, 360 * Math.min(1, progress), false, paint);
                }
            }
        };
        ring.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        face.addView(ring, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout center = column();
        center.setGravity(Gravity.CENTER);
        TextView week = label(caption, 11, ACCENT, true);
        week.setLetterSpacing(.25f);
        center.addView(week, new LinearLayout.LayoutParams(-1, -2));
        TextView title = label(day, 34, TEXT, false);
        title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        title.setPadding(0, dp(6), 0, dp(4));
        center.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView status = label(detail, 12, MUTED, false);
        status.setFontFeatureSettings("tnum");
        center.addView(status, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout controls = row();
        controls.setGravity(Gravity.CENTER);
        ImageButton past = iconButton(R.drawable.ic_history, "Historial", MUTED, history);
        past.setBackground(circle(SURFACE, Color.rgb(47, 57, 50)));
        LinearLayout.LayoutParams small = new LinearLayout.LayoutParams(dp(40), dp(40));
        controls.addView(past, small);
        if (action != null) {
            ImageButton go = iconButton(R.drawable.ic_play, actionDescription, BACKGROUND, action);
            go.setBackground(circle(ACCENT, ACCENT_PRESSED));
            LinearLayout.LayoutParams big = new LinearLayout.LayoutParams(dp(56), dp(56));
            big.leftMargin = dp(10);
            // Balance the history button so the main action sits on the center line.
            View spacer = new View(activity);
            LinearLayout.LayoutParams balance = new LinearLayout.LayoutParams(dp(40), dp(40));
            balance.leftMargin = dp(10);
            controls.addView(go, big);
            controls.addView(spacer, balance);
        }
        LinearLayout.LayoutParams controlsParams = new LinearLayout.LayoutParams(-1, -2);
        controlsParams.topMargin = dp(14);
        center.addView(controls, controlsParams);
        face.addView(center, new FrameLayout.LayoutParams(size - dp(48), -2, Gravity.CENTER));
        body.addView(face, new LinearLayout.LayoutParams(-1, size));
    }

    /**
     * Round watch picker: a spaced caption, a large value between ‹ › arrows, a status dot
     * (green when complete), optional dots for the days inside, and one round confirm action.
     * A null {@code previous} or {@code next} hides that arrow but keeps the value centered.
     */
    void watchPicker(String caption, String value, String status, int statusColor, int[] dots,
            Runnable previous, Runnable next, int actionIcon, String actionDescription, Runnable action) {
        body.setPadding(0, 0, 0, 0);
        int size = Math.min(activity.getResources().getDisplayMetrics().widthPixels,
                activity.getResources().getDisplayMetrics().heightPixels);
        FrameLayout face = new FrameLayout(activity);
        View ring = new View(activity) {
            private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(android.graphics.Canvas canvas) {
                paint.setStyle(android.graphics.Paint.Style.STROKE);
                paint.setStrokeWidth(dp(3));
                paint.setColor(statusColor == GREEN ? GREEN : SURFACE);
                float inset = dp(6);
                canvas.drawOval(inset, inset, getWidth() - inset, getHeight() - inset, paint);
            }
        };
        ring.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        face.addView(ring, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout center = column();
        center.setGravity(Gravity.CENTER);
        TextView label = label(caption, 11, ACCENT, true);
        label.setLetterSpacing(.25f);
        center.addView(label, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout line = row();
        line.setGravity(Gravity.CENTER);
        Runnable[] moves = {previous, next};
        int[] arrows = {R.drawable.ic_chevron_left, R.drawable.ic_chevron_right};
        String[] spoken = {"Anterior", "Siguiente"};
        View[] buttons = new View[2];
        for (int i = 0; i < 2; i++) {
            Runnable move = moves[i];
            ImageButton arrow = iconButton(arrows[i], spoken[i], TEXT, move == null ? () -> {} : move);
            arrow.setBackground(circle(SURFACE, Color.rgb(47, 57, 50)));
            if (move == null) { arrow.setVisibility(View.INVISIBLE); arrow.setEnabled(false); }
            buttons[i] = arrow;
        }
        line.addView(buttons[0], new LinearLayout.LayoutParams(dp(40), dp(40)));
        TextView big = label(value, 52, TEXT, false);
        big.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        big.setGravity(Gravity.CENTER);
        big.setMaxLines(1);
        big.setFontFeatureSettings("tnum");
        line.addView(big, new LinearLayout.LayoutParams(0, -2, 1));
        line.addView(buttons[1], new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(-1, -2);
        lineParams.topMargin = dp(2);
        center.addView(line, lineParams);
        LinearLayout state = row();
        state.setGravity(Gravity.CENTER);
        View dot = new View(activity);
        GradientDrawable mark = new GradientDrawable();
        mark.setShape(GradientDrawable.OVAL);
        mark.setColor(statusColor);
        dot.setBackground(mark);
        state.addView(dot, new LinearLayout.LayoutParams(dp(8), dp(8)));
        TextView text = label(status, 12, statusColor == GREEN ? GREEN : MUTED, false);
        text.setPadding(dp(6), 0, 0, 0);
        state.addView(text, new LinearLayout.LayoutParams(-2, -2));
        center.addView(state, new LinearLayout.LayoutParams(-1, -2));
        if (dots != null) {
            // One small dot per day: green complete, orange started, gray untouched.
            LinearLayout strip = row();
            strip.setGravity(Gravity.CENTER);
            strip.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            for (int d : dots) {
                View pip = new View(activity);
                GradientDrawable shape = new GradientDrawable();
                shape.setShape(GradientDrawable.OVAL);
                shape.setColor(d == 2 ? GREEN : d == 1 ? ACCENT : SURFACE);
                pip.setBackground(shape);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(6), dp(6));
                params.setMargins(dp(3), 0, dp(3), 0);
                strip.addView(pip, params);
            }
            LinearLayout.LayoutParams stripParams = new LinearLayout.LayoutParams(-1, -2);
            stripParams.topMargin = dp(8);
            center.addView(strip, stripParams);
        }
        ImageButton go = iconButton(actionIcon, actionDescription, BACKGROUND, action);
        go.setBackground(circle(ACCENT, ACCENT_PRESSED));
        LinearLayout.LayoutParams goParams = new LinearLayout.LayoutParams(dp(52), dp(52));
        goParams.gravity = Gravity.CENTER_HORIZONTAL;
        goParams.topMargin = dp(12);
        center.addView(go, goParams);
        face.addView(center, new FrameLayout.LayoutParams(size - dp(40), -2, Gravity.CENTER));
        body.addView(face, new LinearLayout.LayoutParams(-1, size));
    }

    private android.graphics.drawable.StateListDrawable circle(int color, int pressed) {
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        GradientDrawable down = new GradientDrawable(); down.setShape(GradientDrawable.OVAL); down.setColor(pressed);
        GradientDrawable up = new GradientDrawable(); up.setShape(GradientDrawable.OVAL); up.setColor(color);
        states.addState(new int[]{android.R.attr.state_pressed}, down);
        states.addState(new int[]{}, up);
        return states;
    }

    /** Square watch action: an icon with a short caption below. */
    private LinearLayout tile(int res, String caption, String description, Runnable click) {
        LinearLayout tile = column();
        tile.setGravity(Gravity.CENTER);
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, background(Color.rgb(47, 57, 50), 24));
        states.addState(new int[]{}, background(SURFACE, 24));
        tile.setBackground(states);
        ImageView image = new ImageView(activity);
        image.setImageDrawable(icon(res, ACCENT));
        tile.addView(image, new LinearLayout.LayoutParams(dp(26), dp(26)));
        TextView text = label(caption, 11, TEXT, false);
        text.setPadding(0, dp(4), 0, 0);
        tile.addView(text, new LinearLayout.LayoutParams(-2, -2));
        tile.setClickable(true);
        tile.setFocusable(true);
        tile.setContentDescription(description);
        tile.setOnClickListener(v -> click.run());
        return tile;
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
