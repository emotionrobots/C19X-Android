package org.c19x.beacon;


import android.bluetooth.BluetoothDevice;

import org.c19x.data.type.BeaconCode;
import org.c19x.data.type.BeaconType;
import org.c19x.data.type.OperatingSystem;
import org.c19x.data.type.RSSI;
import org.c19x.data.type.Time;
import org.c19x.data.type.TimeInterval;

import java.util.Objects;

public class Beacon {
    /// Peripheral underpinning the beacon.
    public final BluetoothDevice peripheral;
    private OperatingSystem operatingSystem;
    private BeaconType beaconType;
    private RSSI rssi;
    private BeaconCode code;
    /**
     * Last update timestamp for beacon code. Need to track this to invalidate codes from
     * yesterday. It is unnecessary to invalidate old codes obtained during a day as the fact
     * that the BLE address is constant (Android) or the connection is open (iOS) means
     * changing the code will offer no security benefit, but increases connection failure risks,
     * especially for Android devices.
     */
    private Time codeUpdatedAt = Time.distantPast;
    /**
     * Last update timestamp for any beacon information. Need to track this to invalidate
     * peripherals that have not been seen for a long time to avoid holding on to an ever
     * growing table of beacons and pending connections to iOS devices. Invalidated
     * beacons can be discovered again in the future by scan instead.
     */
    private Time lastUpdatedAt = Time.distantPast;


    public Beacon(BluetoothDevice peripheral) {
        this.peripheral = peripheral;
    }

    public OperatingSystem getOperatingSystem() {
        return operatingSystem;
    }

    public void setOperatingSystem(OperatingSystem operatingSystem) {
        lastUpdatedAt = new Time();
        this.operatingSystem = operatingSystem;
    }

    public BeaconType getBeaconType() {
        return beaconType;
    }

    public void setBeaconType(BeaconType beaconType) {
        lastUpdatedAt = new Time();
        this.beaconType = beaconType;
    }

    public RSSI getRssi() {
        return rssi;
    }

    public void setRssi(RSSI rssi) {
        lastUpdatedAt = new Time();
        this.rssi = rssi;
    }

    public BeaconCode getCode() {
        return code;
    }

    public void setCode(BeaconCode code) {
        lastUpdatedAt = new Time();
        codeUpdatedAt = new Time();
        this.code = code;
    }

    public String uuid() {
        return peripheral.getAddress();
    }

    /**
     * Beacon is ready if all the information is available (operatingSystem, RSSI, code), and
     * the code was acquired today (day code changes at midnight everyday).
     */
    public boolean isReady() {
        if (code == null || rssi == null) {
            return false;
        }
        final long today = new Time().value.getTime() / 86400000;
        final long createdOnDay = codeUpdatedAt.value.getTime() / 86400000;
        return createdOnDay == today;
    }

    public boolean isExpired() {
        return lastUpdatedAt.timeIntervalSinceNow().value > (3 * TimeInterval.minute.value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Beacon beacon = (Beacon) o;
        return Objects.equals(peripheral.getAddress(), beacon.peripheral.getAddress());
    }

    @Override
    public int hashCode() {
        return Objects.hash(peripheral.getAddress());
    }
}
