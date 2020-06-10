package org.c19x.data;

import android.content.Context;

import androidx.room.Room;

import org.c19x.data.database.ContactDAO;
import org.c19x.data.database.ContactDatabase;
import org.c19x.data.database.ContactEntity;
import org.c19x.data.type.BeaconCode;
import org.c19x.data.type.Contact;
import org.c19x.data.type.RSSI;
import org.c19x.data.type.Time;

import java.util.List;
import java.util.stream.Collectors;

public class ConcreteDatabase implements Database {
    private final static String tag = ConcreteDatabase.class.getName();
    private final ContactDatabase contactDatabase;

    public ConcreteDatabase(Context context) {
        contactDatabase = Room.databaseBuilder(context, ContactDatabase.class, "C19X").build();
        load();
    }

    @Override
    public void insert(Time time, BeaconCode code, RSSI rssi) {
        Logger.debug(tag, "insert (time={},code={},rssi={})", time, code, rssi);
        final Contact contact = new Contact(time, rssi, code);
        final ContactEntity contactEntity = new ContactEntity();
        contactEntity.time = time.value.getTime();
        contactEntity.code = code.value;
        contactEntity.rssi = rssi.value;
        contactDatabase.contactDAO().insertAll(new ContactEntity());
        contacts.add(contact);
    }

    @Override
    public void remove(Time before) {
        Logger.debug(tag, "remove (before={})", before);
        final Thread thread = new Thread(() -> {
            final ContactDAO contactDAO = contactDatabase.contactDAO();
            final List<ContactEntity> contactEntityList = contactDAO.getAll();
            final long beforeTime = before.value.getTime();
            contactEntityList.forEach(contactEntity -> {
                if (contactEntity.time <= beforeTime) {
                    contactDAO.delete(contactEntity);
                }
            });
            load();
        });
        thread.start();
        try {
            thread.join();
        } catch (Throwable e) {
        }
    }

    private final void load() {
        Logger.debug(tag, "load");
        final Thread thread = new Thread(() -> {
            final List<ContactEntity> contactEntityList = contactDatabase.contactDAO().getAll();
            final List<Contact> contactList = contactEntityList.stream().map(e -> new Contact(e)).collect(Collectors.toList());
            contacts.clear();
            contacts.addAll(contactList);
            Logger.debug(tag, "Loaded (count={})", contacts.size());
        });
        thread.start();
        try {
            thread.join();
        } catch (Throwable e) {
        }
    }
}
