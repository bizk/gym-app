package com.example.gym;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import androidx.wear.ongoing.OngoingActivity;
import androidx.wear.ongoing.Status;

/**
 * Marks an open session as a Wear OS ongoing activity, so waking the watch returns to the app
 * instead of the watch face, and the session shows an icon on the watch face.
 */
public final class Ongoing extends Service {
    private static final String CHANNEL = "session";
    private static final int ID = 1;

    static void update(Activity activity, String title, String detail) {
        // The special-use foreground service type needs Android 14 (Wear OS 5).
        if (android.os.Build.VERSION.SDK_INT < 34) return;
        try {
            activity.startForegroundService(new Intent(activity, Ongoing.class)
                    .putExtra("title", title).putExtra("detail", detail));
        } catch (RuntimeException ignored) {
            // Starting is refused while the app is in the background; the next visible change retries.
        }
    }

    /** Asks once per launch for the notification that carries the ongoing activity. */
    static void ask(Activity activity) {
        if (android.os.Build.VERSION.SDK_INT >= 33 && activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED)
            activity.requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 7);
    }

    static void stop(Context context) { context.stopService(new Intent(context, Ongoing.class)); }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (android.os.Build.VERSION.SDK_INT < 34) { stopSelf(); return START_NOT_STICKY; }
        String title = intent == null ? "Sesión en curso" : intent.getStringExtra("title");
        String detail = intent == null ? "" : intent.getStringExtra("detail");
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Sesión en curso", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_training).setContentTitle(title).setContentText(detail)
                .setCategory(NotificationCompat.CATEGORY_WORKOUT).setOngoing(true).setSilent(true)
                .setContentIntent(open);
        new OngoingActivity.Builder(this, ID, builder)
                .setStaticIcon(R.drawable.ic_training).setTouchIntent(open)
                .setStatus(new Status.Builder().addTemplate(title).build())
                .build().apply(this);
        startForeground(ID, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        return START_NOT_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
