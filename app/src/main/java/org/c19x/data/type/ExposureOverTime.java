package org.c19x.data.type;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ExposureOverTime {
    public final Map<ExposurePeriod, RSSI> value = new ConcurrentHashMap<>();

    @Override
    public String toString() {
        return "ExposureOverTime{" +
                "value=" + value +
                '}';
    }
}
