package org.c19x.beacon;

import android.content.Context;

import org.c19x.data.Logger;
import org.c19x.data.type.BeaconCode;
import org.c19x.data.type.BluetoothState;
import org.c19x.data.type.RSSI;
import org.c19x.data.type.SharedSecret;
import org.c19x.data.type.TimeInterval;

public class ConcreteTransceiver implements Transceiver, ReceiverDelegate {
    private final static String tag = ConcreteTransceiver.class.getName();
    private final DayCodes dayCodes;
    private final BeaconCodes beaconCodes;
    private final BluetoothStateManager bluetoothStateManager;
    private final Transmitter transmitter;
    private final Receiver receiver;

    public ConcreteTransceiver(Context context, SharedSecret sharedSecret, TimeInterval codeUpdateAfter) {
        dayCodes = new ConcreteDayCodes(sharedSecret);
        beaconCodes = new ConcreteBeaconCodes(dayCodes);
        bluetoothStateManager = new ConcreteBluetoothStateManager(context);
        transmitter = new ConcreteTransmitter(context, bluetoothStateManager, beaconCodes, codeUpdateAfter);
        receiver = new ConcreteReceiver(context, bluetoothStateManager, transmitter);
        transmitter.delegates.add(this);
        receiver.delegates.add(this);
    }

    @Override
    public void start(String source) {
        transmitter.start(source);
        receiver.start(source);
    }

    @Override
    public void stop(String source) {
        transmitter.stop(source);
        receiver.stop(source);
    }

    @Override
    public void receiver(BeaconCode didDetect, RSSI rssi) {
        Logger.debug(tag, "receiver(didDetect={},rssi={})", didDetect, rssi);
        delegates.forEach(d -> d.receiver(didDetect, rssi));
    }

    @Override
    public void receiver(BluetoothState didUpdateTo) {
        Logger.debug(tag, "receiver(didUpdateTo={})", didUpdateTo);
        delegates.forEach(d -> d.receiver(didUpdateTo));
    }
}
