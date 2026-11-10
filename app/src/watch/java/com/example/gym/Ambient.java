package com.example.gym;

import androidx.activity.ComponentActivity;
import androidx.wear.ambient.AmbientLifecycleObserver;
import androidx.wear.ambient.AmbientLifecycleObserverKt;

/** Keeps the app in front when the watch screen dims or turns off, showing a low-power view. */
final class Ambient {
    interface Listener { void enter(); void update(); void exit(); }

    private Ambient() {}

    static void attach(ComponentActivity activity, Listener listener) {
        activity.getLifecycle().addObserver(AmbientLifecycleObserverKt.AmbientLifecycleObserver(activity,
                new AmbientLifecycleObserver.AmbientLifecycleCallback() {
                    @Override public void onEnterAmbient(AmbientLifecycleObserver.AmbientDetails details) { listener.enter(); }
                    @Override public void onUpdateAmbient() { listener.update(); }
                    @Override public void onExitAmbient() { listener.exit(); }
                }));
    }
}
