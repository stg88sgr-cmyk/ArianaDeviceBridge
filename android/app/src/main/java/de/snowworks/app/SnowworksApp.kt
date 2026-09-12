package de.snowworks.app

import android.app.Application
import de.snowworks.ariana.bridge.AiProviderManager
import de.snowworks.ariana.session.SessionRegistry

class SnowworksApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // No device/session restore after process death.
        SessionRegistry.clear()
        // A previously user-configured provider is only registered in-process.
        // No network connection is opened until a dialogue request is sent.
        AiProviderManager.activateConfigured(this)
    }
}
