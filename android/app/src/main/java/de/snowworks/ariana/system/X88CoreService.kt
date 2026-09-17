package de.snowworks.ariana.system

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder

/**
 * App-process Binder surface for X88. This is intentionally not a system_server
 * service yet; the gateway contract can later be backed by a privileged Binder
 * implementation without changing callers.
 */
class X88CoreService : Service() {

    private lateinit var gateway: X88SystemGateway
    private val binder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        gateway = PersistingX88SystemGateway(
            delegate = InProcessX88SystemGateway(),
            store = SharedPreferencesX88StateStore(this),
        )
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        if (::gateway.isInitialized) {
            gateway.state().activeSessionId?.let(gateway::stopSession)
            gateway.setMasterEnabled(false)
        }
        super.onDestroy()
    }

    inner class LocalBinder : Binder() {
        fun gateway(): X88SystemGateway = gateway
    }
}
