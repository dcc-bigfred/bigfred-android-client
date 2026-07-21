package com.dccbigfred.android

import android.app.Application
import com.dccbigfred.android.data.ServerPreferences
import com.dccbigfred.android.data.localvehicles.LocalVehicleDatabase
import com.dccbigfred.android.data.localvehicles.LocalVehicleRepository
import com.dccbigfred.android.network.BigFredApiClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class BigFredApplication : Application() {
    lateinit var serverPreferences: ServerPreferences
        private set
    lateinit var localVehicleRepository: LocalVehicleRepository
        private set
    lateinit var bigFredApiClient: BigFredApiClient
        private set

    override fun onCreate() {
        super.onCreate()
        serverPreferences = ServerPreferences(this)
        runBlocking {
            serverPreferences.themeMode.first().applyNightMode()
        }
        localVehicleRepository = LocalVehicleRepository(
            LocalVehicleDatabase.get(this).localVehicleDao(),
        )
        bigFredApiClient = BigFredApiClient(serverPreferences)
    }
}
