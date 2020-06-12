package org.c19x.logic;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import org.c19x.AppDelegate;
import org.c19x.data.Logger;
import org.c19x.data.primitive.Tuple;
import org.c19x.data.type.Time;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ForegroundService extends Service {
    private final static String tag = ForegroundService.class.getName();
    private ScheduledExecutorService scheduledExecutorService;

    private void scheduleUpdate() {
        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        scheduledExecutorService.scheduleAtFixedRate(
                () -> {
                    Logger.debug(tag, "Background app refresh (time={})", new Time().description());
                    AppDelegate.getAppDelegate().controller.synchronise(false);
                }, 5, 5, TimeUnit.MINUTES);
    }

    @Override
    public void onCreate() {
        Logger.debug(tag, "onCreate");
        scheduleUpdate();
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
        scheduledExecutorService.shutdown();
        scheduledExecutorService = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}