package com.example.resourcemanager.sqliteNroom

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Snapshot::class], version = 1, exportSchema = false)
abstract class SnapshotRepo : RoomDatabase() {

    abstract fun snapshotDao(): SnapshotDao

    companion object {
        @Volatile
        private var INSTANCE: SnapshotRepo? = null

        fun getDatabase(context: Context): SnapshotRepo {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SnapshotRepo::class.java,
                    "resource_manager_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}