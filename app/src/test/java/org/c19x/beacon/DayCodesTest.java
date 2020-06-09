package org.c19x.beacon;

import org.c19x.data.type.SharedSecret;
import org.junit.Test;

public class DayCodesTest {

    @Test
    public void dayCodes() {
        final DayCodes dayCodes = new ConcreteDayCodes(new SharedSecret(new byte[]{0}));
        System.err.println(dayCodes.day());
        System.err.println(dayCodes.get());
        System.err.println(dayCodes.seed());
        final BeaconCodes beaconCodes = new ConcreteBeaconCodes(dayCodes);
        System.err.println(beaconCodes.get());
    }
}
