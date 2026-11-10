package com.example.gym;

import androidx.activity.ComponentActivity;

/** Phones have no ambient mode. */
final class Ambient {
    interface Listener { void enter(); void update(); void exit(); }

    private Ambient() {}

    static void attach(ComponentActivity activity, Listener listener) {}
}
