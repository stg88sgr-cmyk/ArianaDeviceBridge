package de.snowworks.app

import android.app.Application
import de.snowworks.ariana.session.SessionRegistry

class SnowworksApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // No automatic session restore after process death.
        SessionRegistry.clear()
    }
}
