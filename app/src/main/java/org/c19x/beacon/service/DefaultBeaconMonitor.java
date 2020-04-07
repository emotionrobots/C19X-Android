package org.c19x.beacon.service;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import org.c19x.util.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Beacon monitor for gathering beacon updates.
 */
public class DefaultBeaconMonitor implements BeaconMonitor {
    private final static String tag = DefaultBeaconMonitor.class.getName();
    private final List<BeaconListener> listeners = new ArrayList<>();
    private final Application application;
    private final Class<?> serviceClass;
    private final long ownId;

    private boolean ready = false;
    private AbstractBeaconService beaconService;

    private final BeaconListener beaconServiceListener = new BeaconListener() {
        @Override
        public void status(boolean ready) {
            listeners.forEach(l -> l.status(ready));
        }

        @Override
        public void updateOwnId(long id) {
            listeners.forEach(l -> l.updateOwnId(id));
        }

        @Override
        public void detected(long timestamp, long id, int rssi) {
            listeners.forEach(l -> l.detected(timestamp, id, rssi));
        }
    };

    private ServiceConnection beaconServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            final String name = className.getClassName();
            Logger.debug(tag, "Connected with beacon service (name={})", name);
            if (name.endsWith("BeaconService")) {
                beaconService = ((AbstractBeaconService.BeaconServiceBinder) service).getService();
                beaconService.addListener(beaconServiceListener);
                beaconService.startBeacon(ownId);
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            final String name = className.getClassName();
            Logger.debug(tag, "Disconnected from beacon service (name={})", name);
            if (name.endsWith("BeaconService")) {
                beaconService.removeListener(beaconServiceListener);
                beaconService = null;
                setReady(false);
            }
        }
    };

    public DefaultBeaconMonitor(final Application application, final Class<?> serviceClass, final long ownId) {
        this.application = application;
        this.serviceClass = serviceClass;
        this.ownId = ownId;
    }

    @Override
    public void start() {
        if (beaconService == null) {
            try {
                Logger.debug(tag, "Starting beacon monitor (service={})", serviceClass.getName());
                final Intent intent = new Intent(application, serviceClass);
                application.startService(intent);
                application.bindService(intent, beaconServiceConnection, Context.BIND_AUTO_CREATE);
            } catch (Throwable e) {
                Logger.warn(tag, "Failed to start beacon monitor", e);
            }
        } else {
            Logger.warn(tag, "Ignoring request to start beacon monitor that is already started");
        }
    }

    @Override
    public void stop() {
        if (beaconService != null) {
            Logger.debug(tag, "Stopping beacon monitor (service={})", serviceClass.getName());
            beaconService.stopBeacon();
        } else {
            Logger.warn(tag, "Ignoring request to stop location monitor that is already stopped");
        }
    }

    @Override
    public void addListener(BeaconListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
            Logger.debug(tag, "Added listener (listener={})", listener);
        } else {
            Logger.warn(tag, "Ignoring request to add existing listener again");
        }
    }

    @Override
    public void removeListener(BeaconListener listener) {
        if (listeners.remove(listener)) {
            Logger.debug(tag, "Removed listener (listener={})", listener);
        } else {
            Logger.warn(tag, "Ignoring request to remove unknown listener");
        }
    }

    /**
     * Set and publish ready status.
     *
     * @param ready
     */
    protected void setReady(final boolean ready) {
        this.ready = ready;
        Logger.debug(tag, "Publishing status to listeners (ready={})", ready);
        for (BeaconListener listener : listeners) {
            listener.status(ready);
        }
    }

    @Override
    public boolean isReady() {
        return ready;
    }
}