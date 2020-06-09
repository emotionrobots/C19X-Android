package org.c19x.logic;

import android.content.Context;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.c19x.data.Settings;
import org.c19x.data.primitive.TriConsumer;
import org.c19x.data.primitive.Tuple;
import org.c19x.data.type.ContactPattern;
import org.c19x.data.type.InfectionData;
import org.c19x.data.type.Message;
import org.c19x.data.type.SerialNumber;
import org.c19x.data.type.ServerSettings;
import org.c19x.data.type.SharedSecret;
import org.c19x.data.type.Status;
import org.c19x.data.type.Time;
import org.c19x.data.type.TimeMillis;
import org.c19x.util.Logger;

import java.net.URLEncoder;
import java.util.function.BiConsumer;

public class ConcreteNetwork implements Network {
    private final static String tag = ConcreteNetwork.class.getName();
    private final Settings settings;
    private final RequestQueue requestQueue;

    public ConcreteNetwork(Context context, Settings settings) {
        this.settings = settings;
        requestQueue = Volley.newRequestQueue(context);
    }

    private long getTimestamp() {
        final Tuple<TimeMillis, Time> settingsTimeDelta = settings.timeDelta();
        final long clientTime = System.currentTimeMillis();
        return clientTime + settingsTimeDelta.a.value;
    }

    @Override
    public void synchroniseTime(BiConsumer<TimeMillis, Error> callback) {
        Logger.debug(tag, "Synchronise time request");
        final long clientTime = System.currentTimeMillis();
        final String url = settings.server().value + "time";
        final StringRequest request = new StringRequest(url, response -> {
            try {
                final long serverTime = Long.parseLong(response);
                final long timeDelta = clientTime - serverTime;
                Logger.debug(tag, "Synchronised time with server (delta={})", Long.toString(timeDelta));
                callback.accept(new TimeMillis(timeDelta), null);
            } catch (Throwable e) {
                Logger.warn(tag, "Synchronise time failed, invalid response");
                callback.accept(null, new Error(e));
            }
        }, error -> {
            Logger.warn(tag, "Synchronise time failed, network error (error={})", error);
            callback.accept(null, new Error(error));
        });
        requestQueue.add(request);
    }

    @Override
    public void getRegistration(TriConsumer<SerialNumber, SharedSecret, Error> callback) {
        Logger.debug(tag, "Registration request");
        final String url = settings.server().value + "registration";
        final StringRequest request = new StringRequest(url, response -> {
            try {
                final String[] values = response.split(",");
                final SerialNumber serialNumber = new SerialNumber(Long.parseLong(values[0]));
                final SharedSecret sharedSecret = new SharedSecret(values[1]);
                Logger.debug(tag, "Registration successful (serialNumber={})", serialNumber);
                callback.accept(serialNumber, sharedSecret, null);
            } catch (Throwable e) {
                Logger.warn(tag, "Registration failed, invalid response");
                callback.accept(null, null, new Error(e));
            }
        }, error -> {
            Logger.warn(tag, "Registration failed, network error (error={})", error);
            callback.accept(null, null, new Error(error));
        });
        requestQueue.add(request);
    }

    @Override
    public void getSettings(BiConsumer<ServerSettings, Error> callback) {
        Logger.debug(tag, "Get settings request");
        final String url = settings.server().value + "parameters";
        final StringRequest request = new StringRequest(url, response -> {
            try {
                final ServerSettings serverSettings = new ServerSettings(response);
                Logger.debug(tag, "Get settings successful (settings={})", serverSettings);
                callback.accept(serverSettings, null);
            } catch (Throwable e) {
                Logger.warn(tag, "Get settings failed, invalid response");
                callback.accept(null, new Error(e));
            }
        }, error -> {
            Logger.warn(tag, "Get settings failed, network error (error={})", error);
            callback.accept(null, new Error(error));
        });
        requestQueue.add(request);
    }

    @Override
    public void postStatus(Status status, ContactPattern pattern, SerialNumber serialNumber, SharedSecret sharedSecret, BiConsumer<Status, Error> callback) {
        Logger.debug(tag, "Post status request (status={})", status);
        final String value = Long.toString(getTimestamp()) + "|" + Integer.toString(Status.toValue(status)) + "|" + pattern.value;
        try {
            final String encrypted = AES.encrypt(sharedSecret, value);
            final String encoded = URLEncoder.encode(encrypted, "utf-8");
            final String url = settings.server().value + "status?key=" + Long.toString(serialNumber.value) + "&value=" + encoded;
            final StringRequest request = new StringRequest(Request.Method.POST, url, response -> {
                try {
                    final int rawValue = Integer.parseInt(response);
                    final Status remoteStatus = Status.forValue(rawValue);
                    Logger.debug(tag, "Post status successful (status={})", remoteStatus);
                    callback.accept(remoteStatus, null);
                } catch (Throwable e) {
                    Logger.warn(tag, "Post status failed, invalid response");
                    callback.accept(null, new Error(e));
                }
            }, error -> {
                Logger.warn(tag, "Post status failed, network error (error={})", error);
                callback.accept(null, new Error(error));
            });
            requestQueue.add(request);
        } catch (Throwable e) {
            Logger.warn(tag, "Post status failed, cannot encrypt and encode URL (value={})", value);
            callback.accept(null, new Error(e));
        }
    }

    @Override
    public void getMessage(SerialNumber serialNumber, SharedSecret sharedSecret, BiConsumer<Message, Error> callback) {
        Logger.debug(tag, "Get message request");
        final String value = Long.toString(getTimestamp());
        try {
            final String encrypted = AES.encrypt(sharedSecret, value);
            final String encoded = URLEncoder.encode(encrypted, "utf-8");
            final String url = settings.server().value + "message?key=" + Long.toString(serialNumber.value) + "&value=" + encoded;
            final StringRequest request = new StringRequest(Request.Method.POST, url, response -> {
                try {
                    final Message message = new Message(response == null ? "" : response);
                    Logger.debug(tag, "Get message successful (message={})", message);
                    callback.accept(message, null);
                } catch (Throwable e) {
                    Logger.warn(tag, "Get message failed, invalid response");
                    callback.accept(null, new Error(e));
                }
            }, error -> {
                Logger.warn(tag, "Get message failed, network error (error={})", error);
                callback.accept(null, new Error(error));
            });
            requestQueue.add(request);
        } catch (Throwable e) {
            Logger.warn(tag, "Get message failed, cannot encrypt and encode URL (value={})", value);
            callback.accept(null, new Error(e));
        }
    }

    @Override
    public void getInfectionData(BiConsumer<InfectionData, Error> callback) {
        Logger.debug(tag, "Get infection data request");
        final String url = settings.server().value + "parameters";
        final StringRequest request = new StringRequest(url, response -> {
            try {
                final InfectionData infectionData = new InfectionData(response);
                Logger.debug(tag, "Get infection data successful");
                callback.accept(infectionData, null);
            } catch (Throwable e) {
                Logger.warn(tag, "Get infection data failed, invalid response");
                callback.accept(null, new Error(e));
            }
        }, error -> {
            Logger.warn(tag, "Get infection data failed, network error (error={})", error);
            callback.accept(null, new Error(error));
        });
        requestQueue.add(request);
    }
}
