package org.c19x.data.database;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import org.c19x.data.type.Time;

@Entity
public class ContactEntity {
    @PrimaryKey(autoGenerate = true)
    public int uid;

    @ColumnInfo(name = "time")
    public long time;

    @ColumnInfo(name = "rssi")
    public int rssi;

    @ColumnInfo(name = "code")
    public long code;

    @Override
    public String toString() {
        return "ContactEntity{" +
                "time=" + (new Time(time)).description() +
                ", rssi=" + rssi +
                ", code=" + code +
                '}';
    }
}