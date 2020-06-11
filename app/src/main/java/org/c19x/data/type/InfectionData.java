package org.c19x.data.type;

import org.c19x.data.Logger;
import org.json.JSONObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InfectionData {
    private final static String tag = InfectionData.class.getName();
    public String json = "{}";
    public Map<BeaconCodeSeed, Status> value = new ConcurrentHashMap<>();

    public InfectionData() {
    }

    public InfectionData(final String json) {
        try {
            final JSONObject j = new JSONObject(json);
            j.keys().forEachRemaining(k -> {
                try {
                    final BeaconCodeSeed beaconCodeSeed = new BeaconCodeSeed(Long.parseLong(k));
                    final String v = j.getString(k);
                    final Status status = Status.forRawValue(Integer.parseInt(v));
                    value.put(beaconCodeSeed, status);
                } catch (Throwable e) {
                    Logger.warn(tag, "Failed to parse infection data (key={})", k, e);
                }
            });
            this.json = json;
        } catch (Throwable e) {
            Logger.warn(tag, "Failed to parse json (json={})", json, e);
            this.json = "{}";
        }
    }
}
