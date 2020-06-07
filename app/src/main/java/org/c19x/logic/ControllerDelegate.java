package org.c19x.logic;

import org.c19x.beacon.Transceiver;
import org.c19x.data.type.Advice;
import org.c19x.data.type.BluetoothState;
import org.c19x.data.type.Contact;
import org.c19x.data.type.ControllerState;
import org.c19x.data.type.Message;
import org.c19x.data.type.SerialNumber;
import org.c19x.data.type.Status;
import org.c19x.data.type.Time;

import java.util.List;

public interface ControllerDelegate {
    void controller(ControllerState didUpdateState);

    void registration(SerialNumber serialNumber);

    void transceiver(Transceiver initialised);

    void transceiver(BluetoothState didUpdateState);

    void transceiver(Time didDetectContactAt);

    void message(Message didUpdateTo);

    void database(List<Contact> didUpdateContacts);

    void advice(Advice didUpdateTo, Status contactStatus);

}
