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

import java.util.Deque;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ConcreteDatabase implements Database {
    private final static String tag = ConcreteDatabase.class.getName();
    private final ExecutorService operationQueue = Executors.newSingleThreadExecutor();
    private final ContactDatabase contactDatabase;

    public ConcreteDatabase(Context context, Consumer<Deque<Contact>> callback) {
        contactDatabase = Room.databaseBuilder(context, ContactDatabase.class, "C19X").build();
        load(callback);
    }

    @Override
    public void insert(Time time, BeaconCode code, RSSI rssi, Consumer<Deque<Contact>> callback) {
        final Contact contact = new Contact(time, rssi, code);
        final ContactEntity contactEntity = new ContactEntity();
        contactEntity.time = time.value.getTime();
        contactEntity.code = code.value;
        contactEntity.rssi = rssi.value;
        operationQueue.execute(() -> {
            contactDatabase.contactDAO().insertAll(contactEntity);
            contacts.add(contact);
            Logger.debug(tag, "insert (time={},code={},rssi={})", time, code, rssi);
            callback.accept(contacts);
        });
    }

    @Override
    public void remove(Time before, Consumer<Deque<Contact>> callback) {
        Logger.debug(tag, "remove (before={})", before);
        operationQueue.execute(() -> {
            final ContactDAO contactDAO = contactDatabase.contactDAO();
            final List<ContactEntity> contactEntityList = contactDAO.getAll();
            final long beforeTime = before.value.getTime();
            contactEntityList.forEach(contactEntity -> {
                if (contactEntity.time <= beforeTime) {
                    contactDAO.delete(contactEntity);
                }
            });
            load(callback);
            Logger.debug(tag, "remove successful (before={})", before);
        });
    }

    private final void load(Consumer<Deque<Contact>> callback) {
        Logger.debug(tag, "load");
        operationQueue.execute(() -> {
            final List<ContactEntity> contactEntityList = contactDatabase.contactDAO().getAll();
            final List<Contact> contactList = contactEntityList.stream().map(e -> new Contact(e)).collect(Collectors.toList());
            contacts.clear();
            contacts.addAll(contactList);
            Logger.debug(tag, "Loaded (count={})", contacts.size());
            callback.accept(contacts);
        });
    }
}
