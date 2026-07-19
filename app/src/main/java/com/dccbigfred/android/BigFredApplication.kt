package com.dccbigfred.android

import android.app.Application
import com.dccbigfred.android.data.ServerPreferences

class BigFredApplication : Application() {
    lateinit var serverPreferences: ServerPreferences
        private set

    override fun onCreate() {
        super.onCreate()
        serverPreferences = ServerPreferences(this)
    }
}
