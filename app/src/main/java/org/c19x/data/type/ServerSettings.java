package org.c19x.data.type;

import org.c19x.util.Logger;
import org.json.JSONObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServerSettings {
    private final static String tag = ServerSettings.class.getName();
    public String json = "{}";
    public Map<String, String> value = new ConcurrentHashMap<>();

    public ServerSettings(final String json) {
        try {
            final JSONObject j = new JSONObject(json);
            j.keys().forEachRemaining(k -> {
                try {
                    final String v = j.getString(k);
                    value.put(k, v);
                } catch (Throwable e) {
                    Logger.warn(tag, "Failed to parse json key value (json={},key={})", json, k, e);
                }
            });
            this.json = json;
        } catch (Throwable e) {
            Logger.warn(tag, "Failed to parse json (json={})", json, e);
        }
    }
}
