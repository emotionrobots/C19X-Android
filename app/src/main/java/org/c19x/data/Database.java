package org.c19x.data;

import org.c19x.data.type.BeaconCode;
import org.c19x.data.type.Contact;
import org.c19x.data.type.RSSI;
import org.c19x.data.type.Time;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

public interface Database {
    Deque<Contact> contacts = new ConcurrentLinkedDeque<>();

    /**
     * Add new contact record.
     */
    void insert(Time time, BeaconCode code, RSSI rssi);

    /**
     * Remove all database records before given date.
     */
    void remove(Time before);
}
