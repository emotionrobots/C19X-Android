package org.c19x.data.type;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ExposureProximity {
    public final Map<RSSI, Integer> value = new ConcurrentHashMap<>();

    public ContactPattern contactPattern() {
        return new ContactPattern(value.entrySet().stream().map(e -> Integer.toString(e.getKey().value) + ":" + e.getValue().toString()).collect(Collectors.joining(",", "[", "]")));
    }

    @Override
    public String toString() {
        return "ExposureProximity{" +
                "value=" + value +
                '}';
    }
}
