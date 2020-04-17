package org.c19x.service;

import android.app.IntentService;
import android.app.Notification;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;

import org.c19x.C19XApplication;
import org.c19x.R;
import org.c19x.beacon.BeaconListener;
import org.c19x.gui.ActivityUtil;
import org.c19x.util.Logger;
import org.c19x.util.bluetooth.BluetoothStateMonitorListener;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class BeaconService extends IntentService {
    private final static String tag = BeaconService.class.getName();
    public final static String keyId = "id";
    public final static String keyOnDuration = "onDuration";
    public final static String keyOffDuration = "offDuration";
    public final static String keyActive = "active";

    private final boolean transmitterIsSupported;
    private final boolean receiverIsSupported;


    private final AtomicLong id = new AtomicLong();
    private final AtomicInteger onDuration = new AtomicInteger();
    private final AtomicInteger offDuration = new AtomicInteger();
    private boolean active = true;

    // Bluetooth state listener used by startBeacon and stopBeacon
    private BluetoothStateMonitorListener bluetoothStateMonitorListener = new BluetoothStateMonitorListener() {

        @Override
        public void enabled() {
            Logger.debug(tag, "Bluetooth enabled, starting beacon (transmitter={},receiver={})", transmitterIsSupported, receiverIsSupported);
            id.set(C19XApplication.getAliasIdentifier());
            if (transmitterIsSupported && !C19XApplication.getBeaconTransmitter().isStarted()) {
                Logger.debug(tag, "Starting bluetooth location beacon transmitter");
                C19XApplication.getBeaconTransmitter().start(id.get());
            } else {
                C19XApplication.getBeaconTransmitter().setId(id.get());
            }
            if (receiverIsSupported && !C19XApplication.getBeaconReceiver().isStarted()) {
                Logger.debug(tag, "Starting bluetooth location beacon receiver");
                C19XApplication.getBeaconReceiver().setDutyCycle(onDuration.get(), offDuration.get());
                C19XApplication.getBeaconReceiver().start();
            }
            Logger.debug(tag, "Started beacon");
        }

        @Override
        public void disabling() {
            Logger.debug(tag, "Bluetooth disabling, stopping beacon (transmitter={},receiver={})", transmitterIsSupported, receiverIsSupported);
            if (transmitterIsSupported && C19XApplication.getBeaconTransmitter().isStarted()) {
                Logger.debug(tag, "Stopping bluetooth location beacon transmitter");
                C19XApplication.getBeaconTransmitter().stop();
            }
            if (receiverIsSupported && C19XApplication.getBeaconReceiver().isStarted()) {
                Logger.debug(tag, "Stopping bluetooth location beacon receiver");
                C19XApplication.getBeaconReceiver().stop();
            }
            Logger.debug(tag, "Stopped beacon");
        }
    };

    /**
     * Beacon error detection and recovery
     */
    private BeaconListener beaconErrorListener = new BeaconListener() {
        private AtomicBoolean errorHandling = new AtomicBoolean(false);
        private Handler handler = new Handler(Looper.getMainLooper());
        private BluetoothStateMonitorListener onToOffListener = new BluetoothStateMonitorListener() {
            @Override
            public void disabled() {
                Logger.warn(tag, "Beacon error recovery repair power cycle has disabled bluetooth, attempting to start bluetooth and beacon again in 4 seconds");
                C19XApplication.getBluetoothStateMonitor().removeListener(this);
                C19XApplication.getBluetoothStateMonitor().addListener(offToOnListener);
                try {
                    handler.postDelayed(() -> C19XApplication.getBluetoothStateMonitor().getBluetoothAdapter().enable(), 4000);
                } catch (Throwable e) {
                    Logger.warn(tag, "Bluetooth repair power cycle, failed to enable bluetooth", e);
                }
            }
        };
        private BluetoothStateMonitorListener offToOnListener = new BluetoothStateMonitorListener() {
            @Override
            public void enabled() {
                Logger.warn(tag, "Beacon error recovery repair power cycle has enabled bluetooth, enabling recovery in 10 seconds");
                C19XApplication.getBluetoothStateMonitor().removeListener(this);
                handler.postDelayed(() -> errorHandling.set(false), 10000);
            }
        };

        @Override
        public void error(int errorCode) {
            Logger.warn(tag, "Beacon error (errorCode={},errorHandling={})", errorCode, errorHandling);
            if (errorHandling.compareAndSet(false, true)) {
                Logger.debug(tag, "Beacon error recovery, attempting to power cycle Bluetooth to repair beacon");
                C19XApplication.getBluetoothStateMonitor().addListener(onToOffListener);
                C19XApplication.getBluetoothStateMonitor().getBluetoothAdapter().disable();
            }
        }
    };


    public BeaconService() {
        super("BeaconService");
        this.transmitterIsSupported = C19XApplication.getBeaconTransmitter().isSupported();
        this.receiverIsSupported = C19XApplication.getBeaconReceiver().isSupported();
        Logger.info(tag, "Beacon service, hardware capability check (transmitter={},receiver={})", transmitterIsSupported, receiverIsSupported);
    }

    private void startBeacon() {
        Logger.debug(tag, "Start beacon");
        C19XApplication.getBeaconTransmitter().addListener(beaconErrorListener);
        C19XApplication.getBeaconReceiver().addListener(beaconErrorListener);

        if (!C19XApplication.getBluetoothStateMonitor().isStarted()) {
            C19XApplication.getBluetoothStateMonitor().addListener(bluetoothStateMonitorListener);
            C19XApplication.getBluetoothStateMonitor().start();
            bluetoothStateMonitorListener.enabled();
        }
        // Watchdog process for self-check
        active = true;
        while (active) {
            final boolean transmitter = C19XApplication.getBeaconTransmitter().isStarted();
            final boolean receiver = C19XApplication.getBeaconReceiver().isStarted();
            Logger.debug(tag, "Beacon state (transmitter={},receiver={},activity={})", transmitter, receiver, C19XApplication.getCurrentActivity());
            // If activity is null, the application has terminated
            if (C19XApplication.getCurrentActivity() == null) {
                active = false;
                stopBeacon();
                stopSelf();
                return;
            }
            try {
                Thread.sleep(30000);
            } catch (InterruptedException e) {
            }
        }
    }


    private void stopBeacon() {
        Logger.debug(tag, "Stop beacon");
        C19XApplication.getBeaconTransmitter().removeListener(beaconErrorListener);
        C19XApplication.getBeaconReceiver().removeListener(beaconErrorListener);

        if (C19XApplication.getBluetoothStateMonitor().isStarted()) {
            C19XApplication.getBluetoothStateMonitor().stop();
            C19XApplication.getBluetoothStateMonitor().removeListener(bluetoothStateMonitorListener);
        }
        bluetoothStateMonitorListener.disabling();
        active = false;
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        Logger.debug(tag, "Beacon service handle intent (intent={})", intent);
        id.set(intent.getLongExtra(keyId, C19XApplication.getAliasIdentifier()));
        onDuration.set(intent.getIntExtra(keyOnDuration, 15000));
        offDuration.set(intent.getIntExtra(keyOffDuration, 85000));
        final boolean active = intent.getBooleanExtra(keyActive, true);
        Logger.debug(tag, "Beacon service command (id={},onDuration={},offDuration={},active={})", id, onDuration, offDuration, active);

        if (active) {
            startBeacon();
        } else {
            stopBeacon();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Logger.debug(tag, "Beacon service onCreate");
    }

    @Override
    public void onStart(@Nullable Intent intent, int startId) {
        super.onStart(intent, startId);
        Logger.debug(tag, "Beacon service onStart");
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        Logger.debug(tag, "Beacon service onStartCommand");
        final Notification notification = ActivityUtil.setNotification(this, C19XApplication.getContext().getResources().getString(R.string.app_notification));
        startForeground(Integer.parseInt(C19XApplication.notificationChannelId), notification);
        super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopBeacon();
        C19XApplication.terminate();
        Logger.debug(tag, "Beacon service onDestroy");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Logger.debug(tag, "Beacon service onBind");
        return super.onBind(intent);
    }
}
