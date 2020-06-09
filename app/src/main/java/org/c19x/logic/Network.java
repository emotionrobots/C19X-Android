package org.c19x.logic;

import org.c19x.data.primitive.TriConsumer;
import org.c19x.data.type.ContactPattern;
import org.c19x.data.type.InfectionData;
import org.c19x.data.type.Message;
import org.c19x.data.type.SerialNumber;
import org.c19x.data.type.ServerSettings;
import org.c19x.data.type.SharedSecret;
import org.c19x.data.type.Status;
import org.c19x.data.type.TimeMillis;

import java.util.function.BiConsumer;

public interface Network {
    /**
     * Synchronise time with server to enable authenticated messaging.
     */
    void synchroniseTime(BiConsumer<TimeMillis, Error> callback);

    /**
     * Get registration data from central server.
     */
    void getRegistration(TriConsumer<SerialNumber, SharedSecret, Error> callback);

    /**
     * Get settings from central server.
     */
    void getSettings(BiConsumer<ServerSettings, Error> callback);

    /**
     * Post health status to central server for sharing.
     */
    void postStatus(Status status, ContactPattern pattern, SerialNumber serialNumber, SharedSecret sharedSecret, BiConsumer<Status, Error> callback);

    /**
     * Get personal message from central server.
     */
    void getMessage(SerialNumber serialNumber, SharedSecret sharedSecret, BiConsumer<Message, Error> callback);

    /**
     * Get infection data [BeaconCodeSeed:Status] for on-device matching.
     */
    void getInfectionData(BiConsumer<InfectionData, Error> callback);
}
