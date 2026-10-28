package com.example.gym;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

public class MainActivity extends Activity {
    private JSONObject state;
    private TrainingUi ui;
    private int plan, day, week, exercise;
    private String selectedPlan;
    private boolean watch;
    private String page = "home";
    private CountDownTimer countdown;
    private long restUntil;
    private int restSeconds;
    private int restExercise = -1;
    private long restOfferUntil;
    private boolean restAdvance;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        watch = getPackageManager().hasSystemFeature("android.hardware.type.watch");
        ui = new TrainingUi(this, watch);
        getWindow().setStatusBarColor(TrainingUi.BACKGROUND);
        getWindow().setNavigationBarColor(TrainingUi.BACKGROUND);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        state = Store.read(this);
        if (saved != null) {
            page = saved.getString("page", "home");
            restUntil = saved.getLong("restUntil");
            restSeconds = saved.getInt("restSeconds");
            restExercise = saved.getInt("restExercise", -1);
            restOfferUntil = saved.getLong("restOfferUntil");
            restAdvance = saved.getBoolean("restAdvance");
            selectedPlan = saved.getString("selectedPlan");
            week = saved.getInt("selectedWeek");
        }
        render();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("page", page);
        out.putLong("restUntil", restUntil);
        out.putInt("restSeconds", restSeconds);
        out.putInt("restExercise", restExercise);
        out.putLong("restOfferUntil", restOfferUntil);
        out.putBoolean("restAdvance", restAdvance);
        out.putString("selectedPlan", selectedPlan);
        out.putInt("selectedWeek", week);
    }

    @Override protected void onResume() {
        super.onResume();
        JSONObject latest = Store.read(this);
        if (latest.optLong("revision") > state.optLong("revision")) {
            state = latest;
            // A remote session change invalidates this device's local rest screen.
            cancelRest();
            if (!page.equals("home") && !page.equals("history")) page = "workout";
            render();
        } else if (page.equals("rest")) showRest();
        else if (page.equals("restOffer")) showRestOffer();
    }

    @Override protected void onStop() { super.onStop(); stopTicker(); }
    @Override protected void onDestroy() { stopTicker(); super.onDestroy(); }

    private void render() {
        switch (page) {
            case "workout": workout(); break;
            case "rest": showRest(); break;
            case "history": history(); break;
            case "options": workout(); if (page.equals("workout")) options(); break;
            case "feeling": workout(); break;
            case "restOffer": showRestOffer(); break;
            case "exercises": exercises(); break;
            default: home();
        }
    }

    private void screen(String name) { stopTicker(); page = name; ui.screen(); }
    private void persist() {
        try { state.put("revision", System.currentTimeMillis()); Store.save(this, state, true); }
        catch (Exception e) { error(e); }
    }
    private void error(Exception e) { Toast.makeText(this, e.getMessage() == null ? "No se pudo completar" : e.getMessage(), Toast.LENGTH_LONG).show(); }
    private JSONObject obj(JSONArray array, int index) { return array.optJSONObject(index); }
    private JSONArray plans() { return Store.array(state, "plans"); }
    private JSONObject active() { return state.optJSONObject("active"); }
    private JSONArray items() { return Store.array(active(), "items"); }
    private JSONObject current() { return obj(items(), Math.max(0, Math.min(items().length() - 1, active().optInt("index")))); }
    private String value(JSONObject object, String key) { String value = object.optString(key).trim(); return value.isEmpty() ? "—" : value; }
    private void choices(String title, String[] items, java.util.function.IntConsumer action) {
        new AlertDialog.Builder(this).setTitle(title).setItems(items, (dialog, index) -> action.accept(index)).setNegativeButton("Cancelar", null).show();
    }

    private int completed(JSONArray items) {
        int count = 0;
        for (int i = 0; i < items.length(); i++) count += Math.min(obj(items, i).optInt("sets"), Store.array(obj(items, i), "done").length());
        return count;
    }
    private int total(JSONArray items) {
        int count = 0;
        for (int i = 0; i < items.length(); i++) count += obj(items, i).optInt("sets");
        return count;
    }

    private void home() {
        screen("home");
        ui.eyebrow("MI ENTRENAMIENTO");
        ui.title(active() == null ? "Vamos a entrenar." : "Seguí a tu ritmo.");
        if (active() != null) {
            JSONObject session = active();
            JSONArray list = items();
            if (watch) {
                ui.muted(session.optString("name") + " · S" + session.optInt("week"));
                ui.progress(completed(list), total(list));
            } else {
                LinearLayout card = ui.card();
                ui.cardText(card, "SESIÓN EN CURSO", 12, TrainingUi.ACCENT, true);
                ui.cardText(card, session.optString("name"), 24, TrainingUi.TEXT, true);
                ui.cardText(card, "Semana " + session.optInt("week") + " · " + completed(list) + " de " + total(list) + " series", 15, TrainingUi.MUTED, false);
            }
            ui.button("Continuar sesión", true, this::workout);
        } else if (plans().length() > 0) {
            ui.muted(watch ? "Tu plan está listo." : "Tu plan está listo. Elegí el día y empezá tu próxima sesión.");
            if (watch) ui.button("Empezar sesión", true, this::choosePlan);
        } else {
            ui.muted(watch ? "Importá tu plan en el teléfono para comenzar." : "Tu próximo entrenamiento empieza acá. Importá el Excel de tu entrenador.");
        }
        if (!watch) {
            if (plans().length() > 0) planOverview();
            ui.gap(12);
            ui.eyebrow("TU ESPACIO");
            ui.button(plans().length() > 0 ? "Actualizar plan · Excel" : "Importar plan · Excel", plans().length() == 0, this::importPlan);
        }
        ui.button("Historial", false, this::history);
        if (!watch) {
            ui.gap(16);
            ui.muted("Sin conexión, sin distracciones. Tu progreso se guarda en este dispositivo.");
        }
    }

    private void planOverview() {
        plan = PlanProgress.latest(plans());
        if (selectedPlan != null) {
            for (int i = 0; i < plans().length(); i++)
                if (obj(plans(), i).optString("name").equals(selectedPlan)) { plan = i; break; }
        }
        JSONObject month = obj(plans(), plan);
        selectedPlan = month.optString("name");
        ui.gap(16);
        ui.eyebrow("TU MES");
        ui.button(selectedPlan + "  ▾", false, () -> {
            String[] names = new String[plans().length()];
            for (int i = 0; i < names.length; i++) names[i] = obj(plans(), i).optString("name");
            choices("Elegí el mes / plan", names, n -> { selectedPlan = names[n]; week = 0; home(); });
        });
        int weeks = PlanProgress.weeks(month);
        week = Math.max(0, Math.min(week, weeks - 1));
        JSONArray days = Store.array(month, "days");
        ui.muted("Cada segmento representa un día, en orden.");
        // Two columns keep week labels readable on narrow phones and at larger font sizes.
        LinearLayout row = null;
        for (int w = 0; w < weeks; w++) {
            if (w % 2 == 0) {
                row = ui.row();
                ui.body.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
            int selectedWeek = w;
            int[] status = new int[days.length()];
            for (int d = 0; d < days.length(); d++) {
                JSONObject entry = obj(days, d);
                int count = PlanProgress.completed(state, month, entry, w);
                int total = Store.array(entry, "exercises").length();
                status[d] = total > 0 && count == total ? 2 : count > 0 ? 1 : 0;
            }
            ui.weekButton(row, w + 1, w == week, status, () -> { week = selectedWeek; home(); });
        }
        ui.muted("Verde: completo · Amarillo: parcial · Gris: sin ejercicios completos");
        ui.text("Semana " + (week + 1), 22, TrainingUi.TEXT, true);
        for (int d = 0; d < days.length(); d++) {
            int selectedDay = d;
            JSONObject entry = obj(days, d);
            int count = PlanProgress.completed(state, month, entry, week);
            int total = Store.array(entry, "exercises").length();
            String status = total > 0 && count == total ? "Completo" : count > 0 ? "Parcial" : "Pendiente";
            ui.button(entry.optString("name") + " · " + count + "/" + total + " · " + status, false, () -> {
                if (active() != null) {
                    Toast.makeText(this, "Continuá o finalizá la sesión en curso antes de empezar otra.", Toast.LENGTH_LONG).show();
                    return;
                }
                day = selectedDay; start();
            });
        }
        if (weeks == 0 || days.length() == 0) ui.muted("Este plan no tiene días para entrenar.");
    }

    private void importPlan() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, 1);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != 1 || result != RESULT_OK || data == null) return;
        try (java.io.InputStream in = getContentResolver().openInputStream(data.getData())) {
            state.put("plans", Workbook.parse(in)); selectedPlan = null; week = 0; persist(); home();
            Toast.makeText(this, "Plan importado", Toast.LENGTH_SHORT).show();
        } catch (Exception e) { error(e); }
    }

    private void choosePlan() {
        JSONArray list = plans();
        if (list.length() == 1) { plan = 0; chooseDay(); return; }
        String[] names = new String[list.length()];
        for (int i = 0; i < names.length; i++) names[i] = obj(list, i).optString("name");
        choices("Tu plan", names, n -> { plan = n; chooseDay(); });
    }
    private void chooseDay() {
        JSONArray days = Store.array(obj(plans(), plan), "days");
        String[] names = new String[days.length()];
        for (int i = 0; i < names.length; i++) names[i] = obj(days, i).optString("name");
        choices("¿Qué entrenás hoy?", names, n -> {
            day = n;
            choices("Semana del plan", new String[]{"Semana 1", "Semana 2", "Semana 3", "Semana 4", "Semana 5"}, w -> { week = w; start(); });
        });
    }
    private void start() {
        if (active() != null) { workout(); return; }
        try {
            JSONObject selectedDay = obj(Store.array(obj(plans(), plan), "days"), day);
            JSONArray source = Store.array(selectedDay, "exercises"), list = new JSONArray();
            for (int i = 0; i < source.length(); i++) {
                JSONObject e = obj(source, i);
                JSONObject w = obj(Store.array(e, "weeks"), week);
                if (w == null) w = new JSONObject();
                int sets = 3;
                try { sets = Math.max(1, Math.min(30, (int) Double.parseDouble(w.optString("sets", "3")))); } catch (NumberFormatException ignored) {}
                list.put(new JSONObject().put("name", e.optString("name")).put("code", e.optString("code"))
                        .put("sets", sets).put("done", new JSONArray()).put("reps", w.optString("reps"))
                        .put("load", w.optString("load")).put("video", e.optString("video")));
            }
            state.put("active", new JSONObject().put("name", selectedDay.optString("name")).put("week", week + 1)
                    .put("planName", obj(plans(), plan).optString("name"))
                    .put("started", System.currentTimeMillis()).put("items", list).put("index", 0));
            persist(); workout();
        } catch (Exception e) { error(e); }
    }

    private void workout() {
        if (active() == null) { home(); return; }
        if (restUntil > 0) { showRest(); return; }
        if (items().length() > 0 && current().optBoolean("awaitingFeeling")) {
            JSONObject target = current();
            effort(target, () -> {
                target.remove("awaitingFeeling"); persist(); offerRest();
            });
            return;
        }
        screen("workout");
        JSONArray list = items();
        if (list.length() == 0) {
            ui.title("Sesión vacía"); ui.button("Finalizar sesión", true, this::finishSession); return;
        }
        exercise = Math.max(0, Math.min(list.length() - 1, active().optInt("index")));
        JSONObject e = obj(list, exercise);
        int done = Store.array(e, "done").length(), sets = e.optInt("sets");
        boolean finished = done >= sets || e.optBoolean("closed");
        if (watch) {
            ui.watchExercise(e.optString("name"), value(e, "reps"), value(e, "load"),
                    done, sets, completed(list), total(list), finished,
                    () -> moveTo((exercise + list.length() - 1) % list.length()),
                    this::completeSet, () -> moveTo((exercise + 1) % list.length()), this::undoSet);
            ui.button("Descansar · " + preferredRest() + " s", false, this::startRest);
            if (!finished) ui.button("Terminar serie", false, this::closeExercise);
            ui.button("Modificar carga", false, () -> editLoad(e));
            ui.button("Más opciones", false, this::options);
            return;
        } else {
            ui.eyebrow(active().optString("name").toUpperCase(Locale.getDefault()) + " · SEMANA " + active().optInt("week"));
            ui.muted("Ejercicio " + (exercise + 1) + " de " + list.length());
            ui.progress(completed(list), total(list));
            ui.title(e.optString("name"));
            ui.muted(finished ? "Ejercicio cerrado. " + done + " de " + sets + " series realizadas." : "Concentrate en esta serie. Vos marcás el ritmo.");
            ui.metrics(value(e, "reps"), value(e, "load"));
            ui.eyebrow(finished ? "EJERCICIO COMPLETO" : "SERIE " + (done + 1) + " DE " + sets);
            for (int i = 0; i < sets; i++) {
                LinearLayout row = ui.row();
                row.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14));
                row.setBackground(ui.background(TrainingUi.SURFACE, 16));
                TextView name = ui.label("Serie " + (i + 1), 16, TrainingUi.TEXT, i == done);
                row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
                row.addView(ui.label(i < done ? "✓  Lista" : finished ? "Sin realizar" : i == done ? "Ahora" : "Pendiente", 14,
                        i <= done ? TrainingUi.ACCENT : TrainingUi.MUTED, false));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
                params.bottomMargin = ui.dp(6);
                ui.body.addView(row, params);
            }
            ui.gap(16);
        }
        String action = !finished ? (watch ? "Completar serie" : "✓  Completar serie")
                : completed(list) >= total(list) ? (watch ? "Terminar" : "Finalizar sesión")
                : (watch ? "Siguiente" : "Siguiente ejercicio");
        ui.primary(action, !finished ? this::completeSet : completed(list) >= total(list) ? this::finishSession : this::nextExercise);
        if (!watch) {
            ui.button("Ver ejercicios · " + list.length(), false, this::exercises);
            ui.button("Descanso · " + preferredRest() + " s", false, this::startRest);
            ui.button("Video y ajustes", false, this::options);
        } else ui.button("Más opciones", false, this::options);
    }

    private void completeSet() {
        if (active() == null) return;
        JSONObject e = current();
        JSONArray done = Store.array(e, "done");
        if (done.length() >= e.optInt("sets")) return;
        if (e.optBoolean("closed")) return;
        try {
            done.put(new JSONObject().put("at", System.currentTimeMillis()));
            e.put("done", done);
            if (done.length() >= e.optInt("sets")) {
                e.put("closed", true); e.put("awaitingFeeling", true);
            }
            persist(); haptic(false); workout();
        } catch (Exception ex) { error(ex); }
    }

    private void undoSet() {
        if (active() == null) return;
        JSONObject e = current();
        JSONArray done = Store.array(e, "done");
        if (done.length() == 0) return;
        done.remove(done.length() - 1);
        e.remove("closed"); e.remove("awaitingFeeling"); e.remove("feeling");
        persist(); haptic(false); workout();
    }

    private void closeExercise() {
        try {
            current().put("closed", true).put("awaitingFeeling", true);
            persist(); workout();
        } catch (Exception ex) { error(ex); }
    }

    private void offerRest() {
        restAdvance = true;
        restOfferUntil = SystemClock.elapsedRealtime() + 5000;
        showRestOffer();
    }

    private void showRestOffer() {
        if (active() == null) { home(); return; }
        long remaining = restOfferUntil - SystemClock.elapsedRealtime();
        if (remaining <= 0) { continueAfterExercise(); return; }
        screen("restOffer");
        ui.title("¿Querés descansar?");
        ui.button("Descansar · " + preferredRest() + " s", true, this::startRest);
        ui.button("Seguir", false, this::continueAfterExercise);
        TextView status = ui.text("", 13, TrainingUi.MUTED, false);
        countdown = new CountDownTimer(remaining, 100) {
            public void onTick(long ms) { status.setText(getString(R.string.rest_offer_countdown, (ms + 999) / 1000)); }
            public void onFinish() { continueAfterExercise(); }
        }.start();
    }

    private void continueAfterExercise() {
        restOfferUntil = 0;
        boolean advance = restAdvance;
        cancelRest();
        if (active() == null) { home(); return; }
        if (advance) {
            JSONArray list = items();
            int index = active().optInt("index");
            for (int offset = 1; offset < list.length(); offset++) {
                int next = (index + offset) % list.length();
                JSONObject item = obj(list, next);
                if (!item.optBoolean("closed") && Store.array(item, "done").length() < item.optInt("sets")) {
                    moveTo(next); return;
                }
            }
        }
        workout();
    }

    private void editLoad(JSONObject e) {
        LinearLayout box = ui.column();
        box.setPadding(ui.dp(24), ui.dp(8), ui.dp(24), 0);
        EditText load = field(box, "Carga (con unidad)", e.optString("load"));
        new AlertDialog.Builder(this).setTitle("Modificar carga").setView(box)
                .setNegativeButton("Cancelar", null).setPositiveButton("Guardar", (dialog, n) -> {
                    try { e.put("load", load.getText().toString()); persist(); workout(); }
                    catch (Exception ex) { error(ex); }
                }).show();
    }

    private void haptic(boolean finished) {
        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (finished) vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 90, 100, 90}, -1));
            else vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE));
        }
    }

    private void nextExercise() {
        JSONArray list = items();
        // Find the next unfinished exercise, including one skipped earlier.
        for (int offset = 1; offset <= list.length(); offset++) {
            int next = (exercise + offset) % list.length();
            JSONObject item = obj(list, next);
            if (!item.optBoolean("closed") && Store.array(item, "done").length() < item.optInt("sets")) { moveTo(next); return; }
        }
        finishSession();
    }
    private void moveTo(int index) {
        try { cancelRest(); active().put("index", index); persist(); workout(); }
        catch (Exception e) { error(e); }
    }
    private void exercises() {
        if (active() == null) { home(); return; }
        screen("exercises");
        ui.eyebrow("TU SESIÓN"); ui.title("Ejercicios");
        JSONArray list = items();
        for (int i = 0; i < list.length(); i++) {
            JSONObject e = obj(list, i); int index = i;
            int done = Store.array(e, "done").length();
            ui.button((done >= e.optInt("sets") ? "✓  " : (i + 1) + ". ") + e.optString("name") + "\n" + done + "/" + e.optInt("sets") + " series", false, () -> moveTo(index));
        }
        ui.button("Volver al ejercicio", false, this::workout);
    }
    private void options() {
        if (active() == null || items().length() == 0) { home(); return; }
        if (watch) {
            if (page.equals("options")) return;
            stopTicker(); page = "options";
            ui.overlay(() -> page = "workout");
        } else screen("options");
        JSONObject e = current();
        ui.eyebrow("EN TUS MANOS"); ui.title(watch ? "Opciones" : "Tu ejercicio");
        if (watch) ui.muted(e.optString("name"));
        ui.button("Volver al ejercicio", true, () -> { if (!ui.closeOverlay()) workout(); });
        ui.button("Ver ejercicios", false, this::exercises);
        if (Store.array(e, "done").length() > 0) ui.button("Deshacer último set", false, this::undoSet);
        if (e.optBoolean("closed")) ui.button("Reabrir ejercicio", false, () -> {
            e.remove("closed"); persist(); workout();
        });
        ui.button("¿Cómo me sentí?", false, () -> effort(e, () -> { persist(); workout(); }));
        if (!watch) {
            ui.button("Ver video", false, () -> video(e));
            ui.button("Editar reps, carga y video", false, () -> edit(e));
        }
        ui.gap(12);
        Button finish = ui.action("Finalizar sesión", false, this::finishSession);
        finish.setTextColor(TrainingUi.DANGER);
        ui.body.addView(finish, new LinearLayout.LayoutParams(-1, -2));
        ui.gap(8); ui.button("Ir al inicio", false, this::home);
    }

    private int preferredRest() { return getPreferences(MODE_PRIVATE).getInt("restSeconds", 60); }
    private void chooseRest(Runnable next) {
        choices("Tiempo de descanso", new String[]{"30 segundos", "60 segundos", "90 segundos", "120 segundos"}, n -> {
            getPreferences(MODE_PRIVATE).edit().putInt("restSeconds", new int[]{30, 60, 90, 120}[n]).apply(); next.run();
        });
    }
    private void startRest() {
        restOfferUntil = 0;
        restSeconds = preferredRest(); restUntil = SystemClock.elapsedRealtime() + restSeconds * 1000L;
        restExercise = active().optInt("index"); showRest();
    }
    private void showRest() {
        if (active() == null || restExercise != active().optInt("index")) { cancelRest(); workout(); return; }
        screen("rest");
        ui.eyebrow("RECUPERÁ EL AIRE");
        TextView timer = ui.text("", watch ? 46 : 80, TrainingUi.TEXT, true);
        timer.setGravity(Gravity.CENTER);
        timer.setTypeface(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL));
        timer.setFontFeatureSettings("tnum");
        timer.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_NONE);
        TextView status = ui.text("", watch ? 13 : 20, TrainingUi.MUTED, false);
        status.setGravity(Gravity.CENTER);
        JSONObject e = current();
        ui.primary(watch ? "Seguir" : "Volver al ejercicio", this::continueAfterExercise);
        ui.muted(restAdvance ? "Después: siguiente ejercicio pendiente" : "Después: " + e.optString("name")
                + " · set " + Math.min(e.optInt("sets"), Store.array(e, "done").length() + 1));
        ui.button("+30 segundos", false, () -> {
            restUntil = Math.max(SystemClock.elapsedRealtime(), restUntil) + 30000; showRest();
        });
        ui.button("Modificar descanso", false, () -> chooseRest(this::startRest));
        long remaining = restUntil - SystemClock.elapsedRealtime();
        if (remaining <= 0) { restReady(timer, status, false); return; }
        updateTimer(timer, remaining);
        status.setText(R.string.rest_running);
        countdown = new CountDownTimer(remaining, 200) {
            public void onTick(long ms) { updateTimer(timer, ms); }
            public void onFinish() { restReady(timer, status, true); }
        }.start();
    }
    private void updateTimer(TextView timer, long ms) {
        long seconds = (ms + 999) / 1000;
        timer.setText(String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60));
    }
    private void restReady(TextView timer, TextView status, boolean vibrate) {
        updateTimer(timer, 0); timer.setTextColor(TrainingUi.ACCENT);
        status.setText(R.string.rest_ready);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        if (vibrate) haptic(true);
    }
    private void stopTicker() { if (countdown != null) { countdown.cancel(); countdown = null; } }
    private void cancelRest() { stopTicker(); restUntil = 0; restExercise = -1; restOfferUntil = 0; restAdvance = false; }

    private void effort(JSONObject target, Runnable next) {
        screen("feeling");
        ui.title("¿Cómo te sentiste?");
        String[] names = {"Verde", "Amarillo", "Rojo", "Negro"};
        String[] values = {"verde", "amarillo", "rojo", "negro"};
        int[] colors = {android.graphics.Color.rgb(123, 218, 133), android.graphics.Color.rgb(255, 219, 101),
                android.graphics.Color.rgb(255, 143, 143), android.graphics.Color.BLACK};
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            Button choice = ui.action(names[i], false, () -> {
                try { target.put("feeling", values[index]); next.run(); }
                catch (Exception e) { error(e); }
            });
            android.graphics.drawable.GradientDrawable swatch = ui.background(colors[i], 28);
            swatch.setStroke(ui.dp(1), i == 3 ? TrainingUi.MUTED : colors[i]);
            choice.setBackground(swatch);
            choice.setTextColor(i == 3 ? TrainingUi.TEXT : TrainingUi.BACKGROUND);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.bottomMargin = ui.dp(8);
            ui.body.addView(choice, params);
        }
        ui.button("Cancelar", false, this::dismissFeeling);
    }

    private void dismissFeeling() {
        if (active() != null && items().length() > 0 && current().optBoolean("awaitingFeeling")) {
            current().remove("awaitingFeeling"); persist(); offerRest();
        } else workout();
    }

    private String feelingLabel(JSONObject session) {
        Object feeling = session.opt("feeling");
        if (feeling instanceof Number) return feeling + "/5";
        return feeling == null ? "—" : feeling.toString();
    }

    private void finishSession() {
        if (active() == null) { home(); return; }
        new AlertDialog.Builder(this).setTitle("¿Terminaste por hoy?")
                .setMessage(completed(items()) + " de " + total(items()) + " series completadas. La sesión se guardará en tu historial.")
                .setNegativeButton("Seguir entrenando", null).setPositiveButton("Finalizar", (dialog, n) -> effort(active(), () -> {
                    try {
                        JSONObject session = active(); session.put("finished", System.currentTimeMillis());
                        JSONArray history = Store.array(state, "history"); history.put(session);
                        state.put("history", history); state.remove("active"); cancelRest(); persist(); home();
                    } catch (Exception e) { error(e); }
                })).show();
    }
    private void history() {
        screen("history"); ui.eyebrow("CADA SESIÓN CUENTA"); ui.title("Tu historial");
        JSONArray history = Store.array(state, "history");
        if (history.length() == 0) ui.muted("Tu primera sesión te espera. Cuando termines, la vas a encontrar acá.");
        for (int i = history.length() - 1; i >= 0; i--) {
            JSONObject session = obj(history, i);
            String date = android.text.format.DateFormat.getDateFormat(this).format(new Date(session.optLong("finished")));
            String detail = date + " · Semana " + session.optInt("week");
            JSONArray list = Store.array(session, "items");
            if (watch) { ui.text(session.optString("name"), 18, TrainingUi.TEXT, true); ui.muted(detail); ui.muted(completed(list) + " series · Sensación " + feelingLabel(session)); }
            else {
                LinearLayout card = ui.card();
                ui.cardText(card, detail, 13, TrainingUi.MUTED, false);
                ui.cardText(card, session.optString("name"), 22, TrainingUi.TEXT, true);
                ui.cardText(card, completed(list) + " series · Sensación " + feelingLabel(session), 15, TrainingUi.ACCENT, false);
            }
        }
        ui.button("Volver al inicio", false, this::home);
    }
    private void video(JSONObject e) {
        String url = e.optString("video");
        if (url.isEmpty()) { Toast.makeText(this, "Agregá un enlace en Editar reps, carga y video", Toast.LENGTH_LONG).show(); return; }
        try {
            Uri uri = Uri.parse(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) throw new IllegalArgumentException("Usá un enlace http o https");
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception ex) { error(ex); }
    }
    private void edit(JSONObject e) {
        LinearLayout box = ui.column(); box.setPadding(ui.dp(24), ui.dp(8), ui.dp(24), 0);
        EditText reps = field(box, "Repeticiones", e.optString("reps"));
        EditText load = field(box, "Carga (con unidad)", e.optString("load"));
        EditText video = field(box, "Video · https://…", e.optString("video"));
        video.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        new AlertDialog.Builder(this).setTitle("Editar esta sesión").setView(box).setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", (dialog, n) -> {
                    try { e.put("reps", reps.getText().toString()); e.put("load", load.getText().toString()); e.put("video", video.getText().toString()); persist(); workout(); }
                    catch (Exception ex) { error(ex); }
                }).show();
    }
    private EditText field(LinearLayout box, String name, String value) {
        box.addView(ui.label(name, 13, TrainingUi.MUTED, false));
        EditText input = new EditText(this); input.setText(value); input.setContentDescription(name);
        box.addView(input); return input;
    }
    @Override public void onBackPressed() {
        if (page.equals("options") && ui.closeOverlay()) return;
        if (page.equals("feeling")) { dismissFeeling(); return; }
        if (page.equals("rest") || page.equals("restOffer")) continueAfterExercise();
        else if (page.equals("options") || page.equals("exercises") || page.equals("feeling")) workout();
        else if (!page.equals("home")) home();
        else super.onBackPressed();
    }
}
