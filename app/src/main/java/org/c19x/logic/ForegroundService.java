package org.c19x.logic;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import org.c19x.AppDelegate;
import org.c19x.data.Logger;
import org.c19x.data.primitive.Tuple;

public class ForegroundService extends Service {
    private final static String tag = ForegroundService.class.getName();

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Logger.debug(tag, "Foreground service onStartCommand");
        final Tuple<Integer, Notification> notification = AppDelegate.getAppDelegate().notification("Contact tracing");
        startForeground(notification.a, notification.b);
        super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        AppDelegate.getAppDelegate().onTerminate();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}