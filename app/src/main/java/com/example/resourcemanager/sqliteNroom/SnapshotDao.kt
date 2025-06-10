package com.example.resourcemanager.sqliteNroom

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SnapshotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: Snapshot)

    // Використовуємо LiveData, щоб список автоматично оновлювався
    @Query("SELECT * FROM snapshots WHERE packageName = :packageName ORDER BY timestamp DESC")
    fun getSnapshotsForPackage(packageName: String): LiveData<List<Snapshot>>

    @Query("SELECT * FROM snapshots WHERE id = :snapshotId")
    suspend fun getSnapshotById(snapshotId: Int): Snapshot?
}