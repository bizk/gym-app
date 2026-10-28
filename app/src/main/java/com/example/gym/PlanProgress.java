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
}
