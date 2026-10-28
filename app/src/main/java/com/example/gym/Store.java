package com.example.gym;

import android.content.Context;
import org.json.*;

final class Store {
    static JSONObject read(Context c) {
        try { return new JSONObject(c.getSharedPreferences("gym", 0).getString("state", "{}")); }
        catch (JSONException e) { return new JSONObject(); }
    }
    static void save(Context c, JSONObject state, boolean sync) {
        c.getSharedPreferences("gym", 0).edit().putString("state", state.toString()).apply();
        if (sync) SyncService.send(c, state);
    }
    static JSONArray array(JSONObject o, String key) { JSONArray a = o.optJSONArray(key); return a == null ? new JSONArray() : a; }
}
