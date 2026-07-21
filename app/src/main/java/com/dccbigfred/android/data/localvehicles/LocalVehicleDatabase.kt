package com.dccbigfred.android.data.localvehicles

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [LocalVehicleEntity::class], version = 1, exportSchema = false)
abstract class LocalVehicleDatabase : RoomDatabase() {
    abstract fun localVehicleDao(): LocalVehicleDao

    companion object {
        @Volatile
        private var instance: LocalVehicleDatabase? = null

        fun get(context: Context): LocalVehicleDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LocalVehicleDatabase::class.java,
                    "local_vehicles.db",
                ).build().also { instance = it }
            }
        }
    }
}
