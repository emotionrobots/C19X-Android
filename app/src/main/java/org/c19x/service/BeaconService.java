//package org.c19x.service;
//
//import android.app.IntentService;
//import android.app.Notification;
//import android.content.Intent;
//import android.os.Handler;
//import android.os.IBinder;
//import android.os.Looper;
//
//import androidx.annotation.Nullable;
//
//import org.c19x.AppDelegate;
//import org.c19x.R;
//import org.c19x.gui.ActivityUtil;
//import org.c19x.old.beacon.BeaconListener;
//import org.c19x.data.Logger;
//import org.c19x.util.bluetooth.BluetoothStateMonitorListener;
//
//import java.util.concurrent.atomic.AtomicBoolean;
//import java.util.concurrent.atomic.AtomicInteger;
//import java.util.concurrent.atomic.AtomicLong;
//
//import static android.bluetooth.le.AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED;
//import static android.bluetooth.le.ScanCallback.SCAN_FAILED_ALREADY_STARTED;
//
//public class BeaconService extends IntentService {
//    private final static String tag = BeaconService.class.getName();
//    public final static String keyId = "id";
//    public final static String keyReceiverOnDuration = "receiverOnDuration";
//    public final static String keyReceiverOffDuration = "receiverOffDuration";
//    public final static String keyActive = "active";
//
//    private final boolean transmitterIsSupported;
//    private final boolean receiverIsSupported;
//
//
//    private final AtomicLong id = new AtomicLong();
//    private final AtomicInteger onDuration = new AtomicInteger();
//    private final AtomicInteger offDuration = new AtomicInteger();
//    private boolean active = true;
//
//    // Bluetooth state listener used by startBeacon and stopBeacon
//    private BluetoothStateMonitorListener bluetoothStateMonitorListener = new BluetoothStateMonitorListener() {
//
//        @Override
//        public void enabled() {
//            Logger.debug(tag, "Bluetooth enabled, starting beacon (transmitter={},receiver={})", transmitterIsSupported, receiverIsSupported);
//            id.set(AppDelegate.getAliasIdentifier());
//            if (transmitterIsSupported && !AppDelegate.getBeaconTransmitter().isStarted()) {
//                Logger.debug(tag, "Starting bluetooth location beacon transmitter");
//                AppDelegate.getBeaconTransmitter().start(id.get());
//            } else {
//                AppDelegate.getBeaconTransmitter().setId(id.get());
//            }
//            if (receiverIsSupported && !AppDelegate.getBeaconReceiver().isStarted()) {
//                Logger.debug(tag, "Starting bluetooth location beacon receiver");
//                AppDelegate.getBeaconReceiver().setDutyCycle(onDuration.get(), offDuration.get());
//                AppDelegate.getBeaconReceiver().start();
//            }
//            Logger.debug(tag, "Started beacon");
//        }
//
//        @Override
//        public void disabling() {
//            Logger.debug(tag, "Bluetooth disabling, stopping beacon (transmitter={},receiver={})", transmitterIsSupported, receiverIsSupported);
//            if (transmitterIsSupported && AppDelegate.getBeaconTransmitter().isStarted()) {
//                Logger.debug(tag, "Stopping bluetooth location beacon transmitter");
//                AppDelegate.getBeaconTransmitter().stop();
//            }
//            if (receiverIsSupported && AppDelegate.getBeaconReceiver().isStarted()) {
//                Logger.debug(tag, "Stopping bluetooth location beacon receiver");
//                AppDelegate.getBeaconReceiver().stop();
//            }
//            Logger.debug(tag, "Stopped beacon");
//        }
//    };
//
//    /**
//     * Beacon error detection and recovery
//     */
//    private BeaconListener beaconErrorListener = new BeaconListener() {
//        private AtomicBoolean errorHandling = new AtomicBoolean(false);
//        private Handler handler = new Handler(Looper.getMainLooper());
//        private BluetoothStateMonitorListener onToOffListener = new BluetoothStateMonitorListener() {
//            @Override
//            public void disabled() {
//                Logger.warn(tag, "Beacon error recovery repair power cycle has disabled bluetooth, attempting to start bluetooth and beacon again in 4 seconds");
//                AppDelegate.getBluetoothStateMonitor().removeListener(this);
//                AppDelegate.getBluetoothStateMonitor().addListener(offToOnListener);
//                try {
//                    handler.postDelayed(() -> AppDelegate.getBluetoothStateMonitor().getBluetoothAdapter().enable(), 4000);
//                } catch (Throwable e) {
//                    Logger.warn(tag, "Bluetooth repair power cycle, failed to enable bluetooth", e);
//                }
//            }
//        };
//        private BluetoothStateMonitorListener offToOnListener = new BluetoothStateMonitorListener() {
//            @Override
//            public void enabled() {
//                Logger.warn(tag, "Beacon error recovery repair power cycle has enabled bluetooth, enabling recovery in 10 seconds");
//                AppDelegate.getBluetoothStateMonitor().removeListener(this);
//                handler.postDelayed(() -> errorHandling.set(false), 10000);
//            }
//        };
//
//        @Override
//        public void error(int errorCode) {
//            Logger.warn(tag, "Beacon error (errorCode={},errorHandling={})", errorCode, errorHandling);
//            if (errorCode != ADVERTISE_FAILED_ALREADY_STARTED &&
//                    errorCode != SCAN_FAILED_ALREADY_STARTED &&
//                    errorHandling.compareAndSet(false, true)) {
//                Logger.debug(tag, "Beacon error recovery, attempting to power cycle Bluetooth to repair beacon");
//                AppDelegate.getBluetoothStateMonitor().addListener(onToOffListener);
//                AppDelegate.getBluetoothStateMonitor().getBluetoothAdapter().disable();
//            }
//        }
//    };
//
//
//    public BeaconService() {
//        super("BeaconService");
//        this.transmitterIsSupported = AppDelegate.getBeaconTransmitter().isSupported();
//        this.receiverIsSupported = AppDelegate.getBeaconReceiver().isSupported();
//        Logger.info(tag, "Beacon service, hardware capability check (transmitter={},receiver={})", transmitterIsSupported, receiverIsSupported);
//    }
//
//    private void startBeacon() {
//        Logger.debug(tag, "Start beacon");
//        AppDelegate.getBeaconTransmitter().addListener(beaconErrorListener);
//        AppDelegate.getBeaconReceiver().addListener(beaconErrorListener);
//
//        if (!AppDelegate.getBluetoothStateMonitor().isStarted()) {
//            AppDelegate.getBluetoothStateMonitor().addListener(bluetoothStateMonitorListener);
//            AppDelegate.getBluetoothStateMonitor().start();
//            bluetoothStateMonitorListener.enabled();
//        }
//        // Watchdog process for self-check
//        active = true;
//        while (active) {
//            final boolean transmitter = AppDelegate.getBeaconTransmitter().isStarted();
//            final boolean receiver = AppDelegate.getBeaconReceiver().isStarted();
//            Logger.debug(tag, "Beacon state (transmitter={},receiver={},activity={})", transmitter, receiver, AppDelegate.getCurrentActivity());
//            // If activity is null, the application has terminated
//            if (AppDelegate.getCurrentActivity() == null) {
//                active = false;
//                stopBeacon();
//                stopSelf();
//                return;
//            }
//            try {
//                Thread.sleep(30000);
//            } catch (InterruptedException e) {
//            }
//        }
//    }
//
//
//    private void stopBeacon() {
//        Logger.debug(tag, "Stop beacon");
//        AppDelegate.getBeaconTransmitter().removeListener(beaconErrorListener);
//        AppDelegate.getBeaconReceiver().removeListener(beaconErrorListener);
//
//        if (AppDelegate.getBluetoothStateMonitor().isStarted()) {
//            AppDelegate.getBluetoothStateMonitor().stop();
//            AppDelegate.getBluetoothStateMonitor().removeListener(bluetoothStateMonitorListener);
//        }
//        bluetoothStateMonitorListener.disabling();
//        active = false;
//    }
//
//    @Override
//    protected void onHandleIntent(@Nullable Intent intent) {
//        Logger.debug(tag, "Beacon service handle intent (intent={})", intent);
//        id.set(intent.getLongExtra(keyId, AppDelegate.getAliasIdentifier()));
//        onDuration.set(intent.getIntExtra(keyReceiverOnDuration, 15000));
//        offDuration.set(intent.getIntExtra(keyReceiverOffDuration, 85000));
//        final boolean active = intent.getBooleanExtra(keyActive, true);
//        Logger.debug(tag, "Beacon service command (id={},onDuration={},offDuration={},active={})", id, onDuration, offDuration, active);
//
//        if (active) {
//            startBeacon();
//        } else {
//            stopBeacon();
//        }
//    }
//
//    @Override
//    public void onCreate() {
//        super.onCreate();
//        Logger.debug(tag, "Beacon service onCreate");
//    }
//
//    @Override
//    public void onStart(@Nullable Intent intent, int startId) {
//        super.onStart(intent, startId);
//        Logger.debug(tag, "Beacon service onStart");
//    }
//
//    @Override
//    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
//        Logger.debug(tag, "Beacon service onStartCommand");
//        final Notification notification = ActivityUtil.setNotification(this, AppDelegate.getContext().getResources().getString(R.string.app_notification));
//        startForeground(Integer.parseInt(AppDelegate.notificationChannelId), notification);
//        super.onStartCommand(intent, flags, startId);
//        return START_STICKY;
//    }
//
//    @Override
//    public void onDestroy() {
//        super.onDestroy();
//        stopBeacon();
//        AppDelegate.terminate();
//        Logger.debug(tag, "Beacon service onDestroy");
//    }
//
//    @Nullable
//    @Override
//    public IBinder onBind(Intent intent) {
//        Logger.debug(tag, "Beacon service onBind");
//        return super.onBind(intent);
//    }
//}
