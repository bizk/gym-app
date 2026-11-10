package com.example.gym;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

public class MainActivity extends androidx.activity.ComponentActivity {
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
    /** Direction of the next exercise transition (-1 back, 1 forward), and the set just recorded. */
    private int slide, justCompleted = -1;
    /** Week and day shown on the phone home; -1 picks the current one from recorded progress. */
    private int homeWeek = -1, homeDay = -1;
    /** Plan chosen on the watch picker; -1 opens on the latest imported plan. */
    private int homePlan = -1;

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
        if (watch) Ambient.attach(this, new Ambient.Listener() {
            public void enter() { ui.ambient(ambientSummary()); }
            public void update() { ui.ambient(ambientSummary()); }
            public void exit() { ui.ambient(null); if (page.equals("rest")) showRest(); }
        });
        if (saved != null) {
            page = saved.getString("page", "home");
            restUntil = saved.getLong("restUntil");
            restSeconds = saved.getInt("restSeconds");
            restExercise = saved.getInt("restExercise", -1);
            restOfferUntil = saved.getLong("restOfferUntil");
            restAdvance = saved.getBoolean("restAdvance");
            selectedPlan = saved.getString("selectedPlan");
            homeWeek = saved.getInt("homeWeek", -1);
            homeDay = saved.getInt("homeDay", -1);
            homePlan = saved.getInt("homePlan", -1);
        }
        render();
        if (watch && saved == null && active() != null) Ongoing.ask(this);
        syncOngoing();
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
        out.putInt("homeWeek", homeWeek);
        out.putInt("homeDay", homeDay);
        out.putInt("homePlan", homePlan);
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
            case "edit": editExercise(); break;
            case "pickPlan": pickPlanWatch(); break;
            case "pickWeek": pickWeek(); break;
            case "pickDay": pickDay(); break;
            default: home();
        }
    }

    private void screen(String name) {
        stopTicker(); page = name; ui.screen();
        ui.swipeBack(this::goBack);
    }

    /** One step back from the current screen; from home it leaves the app. */
    private void goBack() {
        switch (page) {
            case "home": finish(); break;
            case "rest": case "restOffer": continueAfterExercise(); break;
            case "feeling": dismissFeeling(); break;
            case "options": if (!ui.closeOverlay()) workout(); break;
            case "edit": hideKeyboard(); workout(); break;
            case "exercises": workout(); break;
            case "pickPlan": home(); break;
            case "pickWeek": pickPlanWatch(); break;
            case "pickDay": pickWeek(); break;
            case "history": if (watch && active() != null) workout(); else home(); break;
            default: home();
        }
    }

    @SuppressWarnings({"deprecation", "MissingSuperCall"})
    @Override public void onBackPressed() { goBack(); }

    private String ambientSummary() {
        JSONObject session = active();
        if (session == null || items().length() == 0) return "Mi entrenamiento";
        JSONObject e = current();
        String top = session.optString("name") + " · S" + session.optInt("week");
        if (page.equals("rest")) return top + "\nDescanso";
        return top + "\n" + e.optString("name") + "\n" + Store.array(e, "done").length() + "/" + e.optInt("sets") + " series";
    }
    private void persist() {
        try { state.put("revision", System.currentTimeMillis()); Store.save(this, state, true); }
        catch (Exception e) { error(e); }
        syncOngoing();
    }

    /** Watch: an open session is an ongoing activity, so waking the watch returns here. */
    private void syncOngoing() {
        if (!watch) return;
        JSONObject session = active();
        if (session == null) { Ongoing.stop(this); return; }
        String detail = items().length() == 0 ? "" : current().optString("name");
        Ongoing.update(this, session.optString("name") + " · Semana " + session.optInt("week"), detail);
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
        if (!watch) { phoneHome(); return; }
        screen("home");
        JSONObject session = active();
        if (session != null) {
            JSONArray list = items();
            int done = completed(list), all = total(list);
            ui.watchHome("SEMANA " + session.optInt("week"), title(session.optString("name")),
                    done + " / " + all + " series", (float) done / Math.max(1, all),
                    "Continuar sesión", this::workout, this::history);
        } else if (plans().length() > 0)
            ui.watchHome("TU PLAN", "Entrenar", "Elegí semana y día", 0, "Empezar sesión",
                    () -> { homePlan = -1; homeWeek = -1; homeDay = -1; pickPlanWatch(); }, this::history);
        else ui.watchHome("TU PLAN", "Entrenar", "Importalo en el teléfono", 0, null, null, this::history);
    }

    /** Status of every day of every week in the plan: 2 complete, 1 started, 0 untouched. */
    private int[][] dayMatrix(JSONObject month) {
        JSONArray days = Store.array(month, "days");
        int[][] status = new int[PlanProgress.weeks(month)][days.length()];
        for (int w = 0; w < status.length; w++)
            for (int d = 0; d < days.length(); d++) status[w][d] = PlanProgress.dayStatus(state, month, obj(days, d), w);
        return status;
    }

    private JSONObject watchPlan() {
        if (homePlan < 0 || homePlan >= plans().length()) homePlan = PlanProgress.latest(plans());
        plan = homePlan;
        return obj(plans(), plan);
    }

    /** Watch step 1: choose the plan, opening on the latest one. */
    private void pickPlanWatch() {
        if (active() != null) { workout(); return; }
        if (plans().length() == 0) { home(); return; }
        JSONObject month = watchPlan();
        // Arrows follow the plan number: ‹ older, › newer, whatever the import order.
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < plans().length(); i++) order.add(i);
        order.sort(Comparator.comparingInt(i -> planNumber(obj(plans(), i).optString("name"))));
        int at = order.indexOf(homePlan);
        int[][] status = dayMatrix(month);
        int[] weeks = new int[status.length];
        for (int w = 0; w < status.length; w++) weeks[w] = PlanProgress.weekStatus(status[w]);
        int overall = weeks.length == 0 ? 0 : PlanProgress.weekStatus(weeks);
        String name = month.optString("name");
        java.util.regex.Matcher number = java.util.regex.Pattern.compile("(\\d+)").matcher(name);
        screen("pickPlan");
        ui.watchPicker("PLAN", number.find() ? number.group(1) : title(name), statusLabel(overall), statusColor(overall), weeks,
                at > 0 ? () -> { homePlan = order.get(at - 1); homeWeek = -1; homeDay = -1; slide = -1; pickPlanWatch(); } : null,
                at < order.size() - 1 ? () -> { homePlan = order.get(at + 1); homeWeek = -1; homeDay = -1; slide = 1; pickPlanWatch(); } : null,
                R.drawable.ic_chevron_right, "Elegir " + name, () -> { homeWeek = -1; homeDay = -1; slide = 1; pickWeek(); });
        ui.slideIn(slide); slide = 0;
    }

    /** Watch step 1: choose the week, opening on the current one. */
    private void pickWeek() {
        if (active() != null) { workout(); return; }
        if (plans().length() == 0) { home(); return; }
        int[][] status = dayMatrix(watchPlan());
        if (status.length == 0 || status[0].length == 0) { home(); return; }
        int[] weeks = new int[status.length];
        for (int w = 0; w < status.length; w++) weeks[w] = PlanProgress.weekStatus(status[w]);
        if (homeWeek < 0 || homeWeek >= status.length) homeWeek = PlanProgress.current(weeks);
        int w = homeWeek;
        screen("pickWeek");
        ui.watchPicker("SEMANA", String.valueOf(w + 1), statusLabel(weeks[w]), statusColor(weeks[w]), status[w],
                w > 0 ? () -> { homeWeek = w - 1; homeDay = -1; slide = -1; pickWeek(); } : null,
                w < status.length - 1 ? () -> { homeWeek = w + 1; homeDay = -1; slide = 1; pickWeek(); } : null,
                R.drawable.ic_chevron_right, "Elegir semana " + (w + 1), () -> { homeDay = -1; slide = 1; pickDay(); });
        ui.slideIn(slide); slide = 0;
    }

    /** Watch step 2: choose the day within the chosen week, then start it. */
    private void pickDay() {
        if (active() != null) { workout(); return; }
        if (plans().length() == 0) { home(); return; }
        JSONObject month = watchPlan();
        int[][] status = dayMatrix(month);
        if (homeWeek < 0 || homeWeek >= status.length || status[homeWeek].length == 0) { pickWeek(); return; }
        int w = homeWeek;
        if (homeDay < 0 || homeDay >= status[w].length) homeDay = PlanProgress.current(status[w]);
        int d = homeDay;
        String name = obj(Store.array(month, "days"), d).optString("name");
        java.util.regex.Matcher number = java.util.regex.Pattern.compile("(\\d+)\\s*$").matcher(name);
        screen("pickDay");
        ui.watchPicker("SEMANA " + (w + 1) + " · DÍA", number.find() ? number.group(1) : title(name),
                statusLabel(status[w][d]), statusColor(status[w][d]), null,
                d > 0 ? () -> { homeDay = d - 1; slide = -1; pickDay(); } : null,
                d < status[w].length - 1 ? () -> { homeDay = d + 1; slide = 1; pickDay(); } : null,
                R.drawable.ic_play, "Empezar " + name, () -> { week = w; day = d; start(); });
        ui.slideIn(slide); slide = 0;
    }

    private static int planNumber(String name) {
        java.util.regex.Matcher number = java.util.regex.Pattern.compile("\\d+").matcher(name);
        try { return number.find() ? Integer.parseInt(number.group()) : Integer.MAX_VALUE; }
        catch (NumberFormatException e) { return Integer.MAX_VALUE; }
    }

    private static String statusLabel(int status) { return status == 2 ? "Completado" : status == 1 ? "En curso" : "Sin empezar"; }
    private static int statusColor(int status) { return status == 2 ? TrainingUi.GREEN : status == 1 ? TrainingUi.ACCENT : TrainingUi.MUTED; }

    /** "DÍA 2" → "Día 2", for a calmer display line. */
    private static String title(String name) {
        String lower = name.toLowerCase(Locale.getDefault());
        return lower.isEmpty() ? lower : lower.substring(0, 1).toUpperCase(Locale.getDefault()) + lower.substring(1);
    }

    private void phoneHome() {
        screen("home");
        if (plans().length() == 0) {
            ui.flex();
            centered(ui.text("MI ENTRENAMIENTO", 12, TrainingUi.ACCENT, true));
            centered(ui.text("Importá tu plan", 32, TrainingUi.TEXT, true));
            centered(ui.text("Elegí el Excel de tu entrenador para empezar.", 15, TrainingUi.MUTED, false));
            ui.flex();
            ui.primary("Importar plan · Excel", this::importPlan);
            return;
        }
        JSONObject session = active();
        // While a session is open, browsing starts from its plan, week and day.
        if (session != null && selectedPlan == null && !session.optString("planName").isEmpty()) selectedPlan = session.optString("planName");
        plan = PlanProgress.latest(plans());
        if (selectedPlan != null)
            for (int i = 0; i < plans().length(); i++)
                if (obj(plans(), i).optString("name").equals(selectedPlan)) { plan = i; break; }
        JSONObject month = obj(plans(), plan);
        selectedPlan = month.optString("name");
        ui.header(selectedPlan, this::pickPlan, R.drawable.ic_history, "Historial", this::history);
        JSONArray days = Store.array(month, "days");
        int weeks = PlanProgress.weeks(month);
        if (weeks == 0 || days.length() == 0) {
            ui.flex(); centered(ui.text("Este plan no tiene días para entrenar.", 15, TrainingUi.MUTED, false)); ui.flex();
            return;
        }
        int[][] status = new int[weeks][days.length()];
        int[] weekStatus = new int[weeks];
        for (int w = 0; w < weeks; w++) {
            for (int d = 0; d < days.length(); d++) status[w][d] = PlanProgress.dayStatus(state, month, obj(days, d), w);
            weekStatus[w] = PlanProgress.weekStatus(status[w]);
        }
        int activeDay = -1;
        if (session != null && session.optString("planName", selectedPlan).equals(selectedPlan))
            for (int i = 0; i < days.length(); i++)
                if (obj(days, i).optString("name").equals(session.optString("name"))) { activeDay = i; break; }
        if (homeWeek < 0 || homeWeek >= weeks) {
            homeWeek = activeDay >= 0 ? Math.max(0, Math.min(weeks - 1, session.optInt("week") - 1)) : PlanProgress.current(weekStatus);
            homeDay = -1;
        }
        if (homeDay < 0 || homeDay >= days.length())
            homeDay = activeDay >= 0 && homeWeek == session.optInt("week") - 1 ? activeDay : PlanProgress.current(status[homeWeek]);
        int w = homeWeek, d = homeDay;
        boolean inSession = activeDay == d && session.optInt("week") == w + 1;
        ui.flex();
        ui.switcher(null, 0, "Semana " + (w + 1), false, null, 0, "Semana",
                w > 0 ? () -> { homeWeek = w - 1; homeDay = -1; home(); } : null,
                w < weeks - 1 ? () -> { homeWeek = w + 1; homeDay = -1; home(); } : null);
        ui.segments(status[w], d);
        ui.gap(48);
        JSONObject entry = obj(days, d);
        JSONArray exercises = Store.array(entry, "exercises");
        int count = PlanProgress.completed(state, month, entry, w);
        String detail; int detailColor;
        if (inSession) { detail = completed(items()) + " de " + total(items()) + " series"; detailColor = TrainingUi.MUTED; }
        else if (status[w][d] == 2) { detail = "✓  Completado"; detailColor = TrainingUi.ACCENT; }
        else if (status[w][d] == 1) { detail = count + " de " + exercises.length() + " ejercicios"; detailColor = TrainingUi.MUTED; }
        else { detail = "Sin empezar"; detailColor = TrainingUi.MUTED; }
        ui.switcher(inSession ? "SESIÓN EN CURSO" : null, TrainingUi.ACCENT, entry.optString("name"), true, detail, detailColor, "Día",
                d > 0 ? () -> { homeDay = d - 1; home(); } : null,
                d < days.length() - 1 ? () -> { homeDay = d + 1; home(); } : null);
        if (inSession) {
            ui.gap(12);
            ui.progress(completed(items()), total(items()));
        }
        ui.gap(inSession ? 8 : 20);
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < Math.min(6, exercises.length()); i++)
            names.append(i > 0 ? "\n" : "").append(obj(exercises, i).optString("name"));
        if (exercises.length() > 6) names.append("\n+ ").append(exercises.length() - 6).append(" más");
        TextView list = centered(ui.text(names.toString(), 14, TrainingUi.MUTED, false));
        list.setLineSpacing(ui.dp(6), 1f);
        ui.flex();
        if (session != null) {
            // Only one session at a time: the action always returns to the open one.
            ui.primary(inSession ? "Continuar sesión" : "Continuar " + session.optString("name") + " · S" + session.optInt("week"), this::workout);
            return;
        }
        String action = status[w][d] == 2 ? "Repetir" : status[w][d] == 1 ? "Seguir" : "Empezar";
        ui.primary(action + " · " + entry.optString("name"), () -> { week = w; day = d; start(); });
    }

    private TextView centered(TextView text) { text.setGravity(Gravity.CENTER); return text; }

    private void pickPlan() {
        String[] names = new String[plans().length() + 1];
        for (int i = 0; i < plans().length(); i++) names[i] = obj(plans(), i).optString("name");
        names[names.length - 1] = "Importar otro Excel…";
        choices("Tu plan", names, n -> {
            if (n == names.length - 1) { importPlan(); return; }
            selectedPlan = names[n]; homeWeek = -1; homeDay = -1; home();
        });
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
            state.put("plans", Workbook.parse(in)); selectedPlan = null; homeWeek = -1; homeDay = -1; persist(); home();
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
            homeWeek = -1; homeDay = -1;
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
            watchFace();
            // Dragging left reveals the history, which follows the finger as a preview.
            ui.swipeForward(this::historyPreview, this::history);
            ui.slideIn(slide); slide = 0; justCompleted = -1;
            return;
        }
        exercisePage(exercise, true);
        String action = !finished ? "✓  Completar serie"
                : completed(list) >= total(list) ? "Finalizar sesión" : "Siguiente ejercicio";
        ui.primary(action, !finished ? this::completeSet : completed(list) >= total(list) ? this::finishSession : this::nextExercise);
        if (list.length() > 1) {
            int previous = (exercise + list.length() - 1) % list.length(), next = (exercise + 1) % list.length();
            // A drag already shows the destination page, so it settles without a second slide.
            ui.pager(() -> exercisePage(previous, false), () -> exercisePage(next, false),
                    () -> moveTo(previous, 0), () -> moveTo(next, 0));
        }
        ui.slideIn(slide); slide = 0; justCompleted = -1;
    }

    /** Watch exercise face for the current exercise, with its action grid below. */
    private void watchFace() {
        JSONArray list = items();
        JSONObject e = current();
        int done = Store.array(e, "done").length(), sets = e.optInt("sets");
        boolean finished = done >= sets || e.optBoolean("closed");
        ui.watchExercise(e.optString("name"), value(e, "reps"), value(e, "load"),
                done, sets, completed(list), total(list), finished,
                this::previousExercise, this::completeSet, this::followingExercise, this::undoSet,
                preferredRest() + " s", this::startRest, this::closeExercise, () -> edit(e), this::options);
    }

    private void historyPreview() {
        ui.body.setPadding(ui.dp(24), ui.dp(22), ui.dp(24), ui.dp(40));
        historyContent();
    }

    /** Phone exercise page; non-live pages are previews shown while swiping. */
    private void exercisePage(int index, boolean live) {
        JSONArray list = items();
        JSONObject e = obj(list, index);
        int done = Store.array(e, "done").length(), sets = e.optInt("sets");
        boolean finished = done >= sets || e.optBoolean("closed");
        ui.topBar(active().optString("name").toUpperCase(Locale.getDefault()) + " · SEMANA " + active().optInt("week"), this::home, this::options);
        ui.progress(completed(list), total(list));
        ui.title(e.optString("name"));
        ui.metrics(value(e, "reps"), value(e, "load"), () -> edit(e));
        ui.eyebrow(finished ? "EJERCICIO COMPLETO" : "SERIE " + (done + 1) + " DE " + sets);
        ui.setRow(done, sets, finished, live ? justCompleted : -1, this::undoSet);
        ui.pair(ui.iconAction(R.drawable.ic_video, "Video", () -> video(e)),
                ui.iconAction(R.drawable.ic_timer, "Descanso", this::startRest));
        ui.button("Ver ejercicios · " + list.length(), false, this::exercises);
    }

    private void previousExercise() { JSONArray list = items(); moveTo((exercise + list.length() - 1) % list.length(), -1); }
    private void followingExercise() { moveTo((exercise + 1) % items().length(), 1); }

    private void completeSet() {
        if (active() == null) return;
        JSONObject e = current();
        JSONArray done = Store.array(e, "done");
        if (done.length() >= e.optInt("sets")) return;
        if (e.optBoolean("closed")) return;
        try {
            done.put(new JSONObject().put("at", System.currentTimeMillis()));
            e.put("done", done);
            justCompleted = done.length() - 1;
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
                    moveTo(next, 1); return;
                }
            }
        }
        workout();
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
            if (!item.optBoolean("closed") && Store.array(item, "done").length() < item.optInt("sets")) { moveTo(next, 1); return; }
        }
        finishSession();
    }
    private void moveTo(int index) { moveTo(index, Integer.compare(index, exercise)); }
    private void moveTo(int index, int direction) {
        try {
            cancelRest(); slide = direction; justCompleted = -1; active().put("index", index);
            workout();
            // Save and sync after the new page is drawn so the transition does not stall.
            getWindow().getDecorView().post(this::persist);
        }
        catch (Exception e) { error(e); }
    }
    private void exercises() {
        if (active() == null) { home(); return; }
        screen("exercises");
        if (!watch) ui.topBar("TU SESIÓN", this::workout); else ui.eyebrow("TU SESIÓN");
        ui.title("Ejercicios");
        JSONArray list = items();
        for (int i = 0; i < list.length(); i++) {
            JSONObject e = obj(list, i); int index = i;
            int done = Store.array(e, "done").length();
            String label = (done >= e.optInt("sets") ? "✓  " : (i + 1) + ". ") + e.optString("name") + "\n" + done + "/" + e.optInt("sets") + " series";
            if (done >= e.optInt("sets")) ui.doneButton(label, () -> moveTo(index));
            else ui.button(label, false, () -> moveTo(index));
        }
        if (watch) ui.button("Volver al ejercicio", false, this::workout);
    }
    private void options() {
        if (active() == null || items().length() == 0) { home(); return; }
        if (watch) {
            if (page.equals("options")) return;
            stopTicker(); page = "options";
            ui.overlay(() -> page = "workout");
        } else screen("options");
        JSONObject e = current();
        if (watch) {
            // Only the two session-level actions; a right drag slides the panel away to the exercise.
            ui.body.setGravity(Gravity.CENTER_VERTICAL);
            ui.title("Opciones");
            ui.gap(8);
            Button finish = ui.action("Terminar sesión", false, this::finishSession);
            finish.setTextColor(TrainingUi.DANGER);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.bottomMargin = ui.dp(8);
            ui.body.addView(finish, params);
            ui.button("Ir al inicio", false, () -> { ui.closeOverlay(); home(); });
            return;
        }
        ui.topBar("EN TUS MANOS", this::workout); ui.title("Opciones"); ui.muted(e.optString("name"));
        ui.button("Ver ejercicios", false, this::exercises);
        if (Store.array(e, "done").length() > 0) ui.button("Deshacer último set", false, this::undoSet);
        if (e.optBoolean("closed")) ui.button("Reabrir ejercicio", false, () -> {
            e.remove("closed"); persist(); workout();
        });
        ui.button("¿Cómo me sentí?", false, () -> effort(e, () -> { persist(); workout(); }));
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
        if (!watch) ui.topBar("RECUPERÁ EL AIRE", this::continueAfterExercise); else ui.eyebrow("RECUPERÁ EL AIRE");
        TextView timer = ui.text("", watch ? 46 : 80, TrainingUi.TEXT, true);
        timer.setGravity(Gravity.CENTER);
        timer.setTypeface(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL));
        timer.setFontFeatureSettings("tnum");
        timer.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_NONE);
        TextView status = ui.text("", watch ? 13 : 20, TrainingUi.MUTED, false);
        status.setGravity(Gravity.CENTER);
        JSONObject e = current();
        ui.primary(watch ? "Seguir" : "Seguir entrenando", this::continueAfterExercise);
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
                        state.put("history", history); state.remove("active"); cancelRest(); persist();
                        homeWeek = -1; homeDay = -1; home();
                    } catch (Exception e) { error(e); }
                })).show();
    }
    private void history() {
        screen("history");
        boolean session = watch && active() != null && items().length() > 0;
        // Dragging right brings the exercise back into view while the history slides away.
        if (session) ui.swipeBackPreview(this::watchFace);
        historyContent();
        if (watch) ui.button(session ? "Volver al ejercicio" : "Volver al inicio", false, this::goBack);
    }

    private void historyContent() {
        if (!watch) ui.topBar("CADA SESIÓN CUENTA", this::home); else ui.eyebrow("CADA SESIÓN CUENTA");
        ui.title("Tu historial");
        JSONArray history = Store.array(state, "history");
        if (history.length() == 0) ui.muted("Tu primera sesión te espera. Cuando termines, la vas a encontrar acá.");
        for (int i = history.length() - 1; i >= 0; i--) {
            JSONObject session = obj(history, i);
            String date = android.text.format.DateFormat.getDateFormat(this).format(new Date(session.optLong("finished")));
            String detail = date + " · Semana " + session.optInt("week");
            JSONArray list = Store.array(session, "items");
            LinearLayout card = null;
            if (watch) { ui.text(session.optString("name"), 18, TrainingUi.TEXT, true); ui.muted(detail); ui.muted(completed(list) + " series · Sensación " + feelingLabel(session)); }
            else {
                card = ui.card();
                ui.cardText(card, detail, 13, TrainingUi.MUTED, false);
                ui.cardText(card, session.optString("name"), 22, TrainingUi.TEXT, true);
                ui.cardText(card, completed(list) + " series · Sensación " + feelingLabel(session), 15, TrainingUi.ACCENT, false);
            }
            for (int j = 0; j < list.length(); j++) {
                JSONObject item = obj(list, j);
                int done = Math.min(item.optInt("sets"), Store.array(item, "done").length());
                ui.exerciseChip(card, item.optString("name"), done, item.optInt("sets"), done >= item.optInt("sets") && done > 0);
            }
            if (watch) ui.gap(12);
        }
    }
    private void video(JSONObject e) {
        String url = e.optString("video");
        if (url.trim().isEmpty()) { edit(e); return; }
        try {
            Uri uri = Uri.parse(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) throw new IllegalArgumentException("Usá un enlace http o https");
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception ex) { error(ex); }
    }
    private void edit(JSONObject e) { editExercise(); }

    /** Full-screen editor for the current exercise's reps, load and video. */
    private void editExercise() {
        if (active() == null || items().length() == 0) { home(); return; }
        JSONObject e = current();
        screen("edit");
        if (!watch) ui.topBar("EDITAR EJERCICIO", this::workout); else ui.eyebrow("EDITAR");
        ui.title(e.optString("name"));
        if (!watch) ui.muted("Los cambios se aplican a esta sesión.");
        ui.gap(watch ? 4 : 8);
        int text = android.text.InputType.TYPE_CLASS_TEXT;
        EditText reps = ui.field("REPETICIONES", e.optString("reps"), "Ej. 10 o 8-10", text, watch ? 0 : 1);
        EditText load = ui.field("CARGA", e.optString("load"), "Ej. 20 kg", text, watch ? 0 : 2.5);
        EditText video = watch ? null : ui.field("VIDEO", e.optString("video"), "https://…",
                text | android.text.InputType.TYPE_TEXT_VARIATION_URI, 0);
        Runnable save = () -> {
            try {
                e.put("reps", reps.getText().toString().trim()); e.put("load", load.getText().toString().trim());
                if (video != null) e.put("video", video.getText().toString().trim());
                hideKeyboard(); persist(); workout();
            } catch (Exception ex) { error(ex); }
        };
        ui.primary("Guardar cambios", save);
        if (watch) ui.button("Cancelar", false, this::workout);
    }

    private void hideKeyboard() {
        View focus = getCurrentFocus();
        if (focus != null) ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(focus.getWindowToken(), 0);
    }
}
