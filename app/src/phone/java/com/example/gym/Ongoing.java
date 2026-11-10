package com.example.gym;

import android.app.Activity;
import android.content.Context;

/** Phones keep the app in recents; no ongoing activity is needed. */
final class Ongoing {
    private Ongoing() {}
    static void update(Activity activity, String title, String detail) {}
    static void ask(Activity activity) {}
    static void stop(Context context) {}
}
