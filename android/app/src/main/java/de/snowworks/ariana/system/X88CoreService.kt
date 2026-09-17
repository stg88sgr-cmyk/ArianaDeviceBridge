package de.snowworks.ariana.system

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder

/**
 * App-process Binder surface for X88. The service exposes the same canonical
 * process runtime used by ArianaGate and the local bridge.
 */
class X88CoreService : Service() {

    private lateinit var gateway: X88SystemGateway
    private val binder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        gateway = X88Runtime.gateway(this)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    inner class LocalBinder : Binder() {
        fun gateway(): X88SystemGateway = gateway
    }
}
