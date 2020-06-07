package org.c19x.gui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.c19x.C19XApplication;
import org.c19x.R;
import org.c19x.beacon.BeaconCodes;
import org.c19x.beacon.ConcreteBeaconCodes;
import org.c19x.beacon.ConcreteDayCodes;
import org.c19x.beacon.ConcreteReceiver;
import org.c19x.beacon.ConcreteTransmitter;
import org.c19x.beacon.DayCodes;
import org.c19x.beacon.Receiver;
import org.c19x.beacon.Transmitter;
import org.c19x.data.type.TimeInterval;
import org.c19x.util.Logger;

public class TestActivity extends AppCompatActivity {
    private final static String tag = TestActivity.class.getName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);

        final String locationPermission = Manifest.permission.ACCESS_FINE_LOCATION;
        if (ActivityCompat.checkSelfPermission(this, locationPermission) != PackageManager.PERMISSION_GRANTED) {
            Logger.debug(tag, "Requesting access location permission");
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, locationPermission)) {
                ActivityUtil.showDialog(this,
                        R.string.dialog_welcome_permission_location_title,
                        R.string.dialog_welcome_permission_location_rationale,
                        () -> ActivityCompat.requestPermissions(this, new String[]{locationPermission}, 0), () -> finish());
            } else {
                ActivityCompat.requestPermissions(this, new String[]{locationPermission}, 0);
            }
        }

        final DayCodes dayCodes = new ConcreteDayCodes(new byte[]{0});
        final BeaconCodes beaconCodes = new ConcreteBeaconCodes(dayCodes);
        final Transmitter transmitter = new ConcreteTransmitter(C19XApplication.getContext(), beaconCodes, new TimeInterval(120));
        final Receiver receiver = new ConcreteReceiver(C19XApplication.getContext(), transmitter);
//
//        final BluetoothStateMonitor bluetoothStateMonitor = new BluetoothStateMonitor();
//        final BeaconTransmitter beaconTransmitter = C19XApplication.getBeaconTransmitter();
//        final BeaconReceiver beaconReceiver = C19XApplication.getBeaconReceiver();
//        beaconReceiver.setDutyCycle(5000, 2000);
//        final long id = 5569969605707727818l;
//        bluetoothStateMonitor.addListener(new BluetoothStateMonitorListener() {
//            @Override
//            public void enabled() {
//                beaconTransmitter.start(id);
//                beaconReceiver.start();
//            }
//
//            @Override
//            public void disabling() {
//                beaconTransmitter.stop();
//                beaconReceiver.stop();
//            }
//        });
//        bluetoothStateMonitor.start();
//        if (bluetoothStateMonitor.isEnabled()) {
//            beaconTransmitter.start(id);
//            beaconReceiver.start();
//        }
    }

}
