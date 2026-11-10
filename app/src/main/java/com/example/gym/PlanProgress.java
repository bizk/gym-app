package com.example.gym;

import org.json.*;

/** Progress belongs to a plan/week/day, never to the calendar date of a workout. */
final class PlanProgress {
    static int latest(JSONArray plans) {
        int selected = 0, highest = -1;
        for (int i = 0; i < plans.length(); i++) {
            String name = plans.optJSONObject(i).optString("name");
            java.util.regex.Matcher number = java.util.regex.Pattern.compile("\\d+").matcher(name);
            if (number.find()) {
                int order;
                try { order = Integer.parseInt(number.group()); } catch (NumberFormatException ignored) { continue; }
                if (order > highest) { highest = order; selected = i; }
            }
        }
        return selected;
    }

    static int weeks(JSONObject plan) {
        int count = 0;
        JSONArray days = Store.array(plan, "days");
        for (int d = 0; d < days.length(); d++) {
            JSONArray exercises = Store.array(days.optJSONObject(d), "exercises");
            for (int e = 0; e < exercises.length(); e++)
                count = Math.max(count, Store.array(exercises.optJSONObject(e), "weeks").length());
        }
        return count;
    }

    static String sessionPlan(JSONObject state, JSONObject session) {
        if (session.has("planName")) return session.optString("planName");
        // Older sessions had no plan reference. Attribute only an unambiguous exercise list.
        JSONArray plans = Store.array(state, "plans"), items = Store.array(session, "items");
        String match = "";
        int candidates = 0;
        for (int p = 0; p < plans.length(); p++) {
            JSONObject plan = plans.optJSONObject(p);
            JSONArray days = Store.array(plan, "days");
            for (int d = 0; d < days.length(); d++) {
                JSONObject day = days.optJSONObject(d);
                JSONArray exercises = Store.array(day, "exercises");
                if (!day.optString("name").equals(session.optString("name"))
                        || exercises.length() == 0 || exercises.length() != items.length()) continue;
                boolean same = true;
                for (int e = 0; e < exercises.length(); e++) {
                    JSONObject expected = exercises.optJSONObject(e), item = items.optJSONObject(e);
                    if (!expected.optString("code").equals(item.optString("code"))
                            || !expected.optString("name").equals(item.optString("name"))) { same = false; break; }
                }
                if (same) { match = plan.optString("name"); candidates++; }
            }
        }
        return candidates == 1 ? match : "";
    }

    static boolean matches(JSONObject state, JSONObject session, JSONObject plan, JSONObject day, int week) {
        return session != null && sessionPlan(state, session).equals(plan.optString("name"))
                && session.optString("name").equals(day.optString("name")) && session.optInt("week") == week + 1;
    }

    static void collect(boolean[] done, JSONObject session, JSONArray exercises) {
        JSONArray items = Store.array(session, "items");
        for (int e = 0; e < exercises.length(); e++) {
            JSONObject expected = exercises.optJSONObject(e);
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item.optString("code").equals(expected.optString("code"))
                        && item.optString("name").equals(expected.optString("name"))
                        && item.optInt("sets") > 0 && Store.array(item, "done").length() >= item.optInt("sets")) done[e] = true;
            }
        }
    }

    static int completed(JSONObject state, JSONObject plan, JSONObject day, int week) {
        JSONArray exercises = Store.array(day, "exercises");
        boolean[] done = new boolean[exercises.length()];
        JSONArray history = Store.array(state, "history");
        for (int i = 0; i < history.length(); i++) {
            JSONObject session = history.optJSONObject(i);
            if (matches(state, session, plan, day, week)) collect(done, session, exercises);
        }
        JSONObject active = state.optJSONObject("active");
        if (matches(state, active, plan, day, week)) collect(done, active, exercises);
        int count = 0;
        for (boolean complete : done) if (complete) count++;
        return count;
    }

    /** True when any session for this plan/day/week recorded at least one set. */
    static boolean started(JSONObject state, JSONObject plan, JSONObject day, int week) {
        JSONArray history = Store.array(state, "history");
        for (int i = 0; i <= history.length(); i++) {
            JSONObject session = i < history.length() ? history.optJSONObject(i) : state.optJSONObject("active");
            if (!matches(state, session, plan, day, week)) continue;
            JSONArray items = Store.array(session, "items");
            for (int e = 0; e < items.length(); e++)
                if (Store.array(items.optJSONObject(e), "done").length() > 0) return true;
        }
        return false;
    }

    /** 2 = every exercise completed, 1 = some work recorded, 0 = untouched. */
    static int dayStatus(JSONObject state, JSONObject plan, JSONObject day, int week) {
        int total = Store.array(day, "exercises").length();
        int count = completed(state, plan, day, week);
        if (total > 0 && count == total) return 2;
        return count > 0 || started(state, plan, day, week) ? 1 : 0;
    }

    static int weekStatus(int[] days) {
        boolean all = days.length > 0, any = false;
        for (int day : days) { all &= day == 2; any |= day > 0; }
        return all ? 2 : any ? 1 : 0;
    }

    /**
     * The entry to show by default: the last one with recorded work, or the one after it when that
     * one is already complete. Untouched sequences start at the first entry.
     */
    static int current(int[] status) {
        for (int i = status.length - 1; i >= 0; i--)
            if (status[i] > 0) return status[i] == 2 ? Math.min(i + 1, status.length - 1) : i;
        return 0;
    }
}
