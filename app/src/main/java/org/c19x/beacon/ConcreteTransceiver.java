package org.c19x.beacon;

import android.content.Context;

import org.c19x.data.type.SharedSecret;
import org.c19x.data.type.TimeInterval;

public class ConcreteTransceiver implements Transceiver {
    private final DayCodes dayCodes;
    private final BeaconCodes beaconCodes;
    private final Transmitter transmitter;
    private final Receiver receiver;

    public ConcreteTransceiver(Context context, SharedSecret sharedSecret, TimeInterval codeUpdateAfter) {
        dayCodes = new ConcreteDayCodes(sharedSecret);
        beaconCodes = new ConcreteBeaconCodes(dayCodes);
        transmitter = new ConcreteTransmitter(context, beaconCodes, codeUpdateAfter);
        receiver = new ConcreteReceiver(context, transmitter);
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
    public void append(ReceiverDelegate delegate) {
        receiver.delegates.add(delegate);
        transmitter.delegates.add(delegate);
    }
}
