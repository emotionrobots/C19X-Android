package org.c19x.data.database;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class ContactEntity {
    @PrimaryKey
    public int uid;

    @ColumnInfo(name = "time")
    public long time;

    @ColumnInfo(name = "rssi")
    public int rssi;

    @ColumnInfo(name = "code")
    public long code;
}