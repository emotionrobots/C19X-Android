package org.c19x.data.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ContactDAO {

    @Query("SELECT * FROM contactentity")
    List<ContactEntity> getAll();

    @Insert
    void insertAll(ContactEntity... contactEntities);

    @Delete
    void delete(ContactEntity contactEntity);
}
