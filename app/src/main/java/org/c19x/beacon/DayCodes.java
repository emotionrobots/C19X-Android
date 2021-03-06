package org.c19x.beacon;

import org.c19x.data.primitive.Tuple;
import org.c19x.data.type.BeaconCodeSeed;
import org.c19x.data.type.Day;
import org.c19x.data.type.DayCode;

public interface DayCodes {

    Day day();

    DayCode get();

    Tuple<BeaconCodeSeed, Day> seed();

}
