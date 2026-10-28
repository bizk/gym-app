package com.example.gym;

import android.content.Context;
import com.google.android.gms.wearable.*;
import org.json.JSONObject;

public class SyncService extends WearableListenerService {
    static void send(Context c, JSONObject state) {
        PutDataMapRequest req = PutDataMapRequest.create("/gym/state");
        req.getDataMap().putString("state", state.toString());
        req.getDataMap().putLong("revision", state.optLong("revision"));
        Wearable.getDataClient(c).putDataItem(req.asPutDataRequest().setUrgent());
    }
    @Override public void onDataChanged(DataEventBuffer events) {
        for (DataEvent event : events) {
            if (event.getType() != DataEvent.TYPE_CHANGED || !"/gym/state".equals(event.getDataItem().getUri().getPath())) continue;
            try {
                JSONObject incoming = new JSONObject(DataMapItem.fromDataItem(event.getDataItem()).getDataMap().getString("state", "{}"));
                if (incoming.optLong("revision") > Store.read(this).optLong("revision")) Store.save(this, incoming, false);
            } catch (Exception ignored) { }
        }
    }
}
