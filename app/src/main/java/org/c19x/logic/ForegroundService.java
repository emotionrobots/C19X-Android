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
        Logger.debug(tag, "onCreate");
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Logger.debug(tag, "onStartCommand");
        final Tuple<Integer, Notification> notification = AppDelegate.getAppDelegate().notification("Contact Tracing Enabled", "Turn off Bluetooth to pause.");
        startForeground(notification.a, notification.b);
        super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Logger.debug(tag, "onDestroy");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}