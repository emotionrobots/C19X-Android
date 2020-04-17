package org.c19x.beacon.serviceOld;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import org.c19x.util.Logger;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractBeaconService extends Service implements BeaconService {
    private final static String tag = AbstractBeaconService.class.getName();
    protected final static int serviceId = 390719873;
    protected final List<BeaconListener> listeners = new ArrayList<>();

    public final class BeaconServiceBinder extends Binder {
        public AbstractBeaconService getService() {
            return AbstractBeaconService.this;
        }
    }

    private final BeaconServiceBinder beaconServiceBinder = new BeaconServiceBinder();

    /**
     * Add listener for receiving beacon events.
     *
     * @param listener
     */
    public void addListener(BeaconListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Remove listener for receiving beacon events.
     *
     * @param listener
     */
    public void removeListener(BeaconListener listener) {
        if (listeners.contains(listener)) {
            listeners.remove(listener);
        }
    }

    @Override
    public void onCreate() {
        Logger.debug(tag, "Creating beacon service");
        startForeground(serviceId, getNotification());
        super.onCreate();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return beaconServiceBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Logger.debug(tag, "Destroying beacon service");
        stopBeacon();
        listeners.clear();
        Logger.debug(tag, "Destroyed beacon service");
        super.onDestroy();
    }

    /**
     * Make this application a foreground application with an active notification. This is necessary to get round request limits.
     *
     * @return
     */
    protected Notification getNotification() {
        NotificationChannel channel = new NotificationChannel("c19xChannel1", "C19X Channel", NotificationManager.IMPORTANCE_DEFAULT);
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
        Notification.Builder builder = new Notification.Builder(getApplicationContext(), "c19xChannel1").setAutoCancel(true);
        return builder.build();
    }

    /**
     * Start beacon service and make it a foreground service.
     *
     * @param ownId Own identifier
     */
    public void startBeacon(long ownId) {
        startForeground(serviceId, getNotification());
        startService(ownId);
    }

    /**
     * Stop beacon service and remove it from foreground services.
     */
    public void stopBeacon() {
        stopService();
        stopForeground(true);
    }
}