package com.example.gym;

import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PlanProgressTest {
    private JSONObject exercise(String code, int done) throws Exception {
        JSONArray sets = new JSONArray();
        for (int i = 0; i < done; i++) sets.put(new JSONObject());
        return new JSONObject().put("code", code).put("name", code).put("sets", 3).put("done", sets);
    }

    private JSONObject plan(String name) throws Exception {
        return new JSONObject().put("name", name).put("days", new JSONArray().put(
                new JSONObject().put("name", "Día 1").put("exercises", new JSONArray()
                        .put(exercise("A1", 0)).put(exercise("A2", 0)))));
    }

    private JSONObject session(String plan, int week, int first, int second) throws Exception {
        return new JSONObject().put("planName", plan).put("name", "Día 1").put("week", week)
                .put("items", new JSONArray().put(exercise("A1", first)).put(exercise("A2", second)));
    }

    private int count(JSONObject state, JSONObject plan) {
        return PlanProgress.completed(state, plan, Store.array(plan, "days").optJSONObject(0), 0);
    }

    @Test public void latestPlanUsesNumberInsteadOfSheetOrder() throws Exception {
        assertEquals(1, PlanProgress.latest(new JSONArray().put(plan("6° Plan"))
                .put(plan("10º Plan")).put(plan("7º Plan"))));
    }

    @Test public void earlyCloseAndPartialSetsDoNotCompleteAnExercise() throws Exception {
        JSONObject plan = plan("7º Plan"), active = session("7º Plan", 1, 2, 0);
        Store.array(active, "items").optJSONObject(0).put("closed", true);
        JSONObject state = new JSONObject().put("active", active);
        assertEquals(0, count(state, plan));
        state.put("active", session("7º Plan", 1, 3, 0));
        assertEquals(1, count(state, plan));
        state.put("active", session("7º Plan", 1, 3, 3));
        assertEquals(2, count(state, plan));
    }

    @Test public void mergesHistoryAndActiveWithoutDuplicatesOrOtherMonthsAndWeeks() throws Exception {
        JSONObject plan = plan("7º Plan");
        JSONObject state = new JSONObject().put("history", new JSONArray()
                .put(session("7º Plan", 1, 3, 0)).put(session("7º Plan", 1, 3, 0))
                .put(session("6° Plan", 1, 3, 3)).put(session("7º Plan", 2, 3, 3)));
        assertEquals(1, count(state, plan));
        state.put("active", session("7º Plan", 1, 0, 3));
        assertEquals(2, count(state, plan));
    }

    @Test public void legacySessionsAreAttributedOnlyWhenUnambiguous() throws Exception {
        JSONObject plan = plan("7º Plan"), session = session("7º Plan", 1, 3, 3);
        session.remove("planName");
        JSONObject state = new JSONObject().put("plans", new JSONArray().put(plan)).put("active", session);
        assertEquals(2, count(state, plan));
        state.put("plans", new JSONArray().put(plan).put(plan("6° Plan")));
        assertEquals(0, count(state, plan));
    }
}
