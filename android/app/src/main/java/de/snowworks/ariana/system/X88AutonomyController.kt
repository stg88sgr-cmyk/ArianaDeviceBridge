package de.snowworks.ariana.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import de.snowworks.ariana.ArianaGate

/**
 * Process-local self-healing supervisor for X88.
 *
 * Guarantees:
 * - never enables the user master gate itself
 * - never enables capabilities by itself
 * - Stop-All always wins
 * - owns at most one X88 session
 * - reconnects the app Binder if the process remains alive
 */
object X88AutonomyController {
    private const val HEARTBEAT_MS = 30_000L

    private lateinit var appContext: Context
    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var gateway: X88SystemGateway? = null

    @Volatile
    private var bound = false

    @Volatile
    private var ownedSessionId: String? = null

    private val heartbeat = object : Runnable {
        override fun run() {
            reconcile()
            handler.postDelayed(this, HEARTBEAT_MS)
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val local = service as? X88CoreService.LocalBinder ?: return
            gateway = local.gateway()
            bound = true
            reconcile()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            gateway = null
            bound = false
            ownedSessionId = null
            scheduleImmediateReconnect()
        }

        override fun onBindingDied(name: ComponentName?) {
            gateway = null
            bound = false
            ownedSessionId = null
            scheduleImmediateReconnect()
        }
    }

    @Synchronized
    fun initialize(context: Context) {
        appContext = context.applicationContext
        handler.removeCallbacks(heartbeat)
        bindIfNeeded()
        handler.post(heartbeat)
    }

    fun refresh() = handler.post { reconcile() }

    @Synchronized
    fun shutdown() {
        stopOwnedSession()
        gateway?.setMasterEnabled(false)
        handler.removeCallbacks(heartbeat)
        if (bound) {
            runCatching { appContext.unbindService(connection) }
        }
        gateway = null
        bound = false
    }

    private fun scheduleImmediateReconnect() {
        if (!::appContext.isInitialized) return
        handler.postDelayed({ bindIfNeeded() }, 1_000L)
    }

    @Synchronized
    private fun bindIfNeeded() {
        if (!::appContext.isInitialized || bound) return
        val intent = Intent(appContext, X88CoreService::class.java)
        val ok = runCatching {
            appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }.getOrDefault(false)
        if (!ok) bound = false
    }

    @Synchronized
    private fun reconcile() {
        if (!::appContext.isInitialized) return
        if (!bound) bindIfNeeded()

        val gate = ArianaGate(appContext)
        val decision = X88AutonomyPolicy.decide(
            userMasterEnabled = gate.isMasterEnabled,
            stopAllBlocked = gate.isBlocked,
        )

        val current = gateway ?: return
        if (!decision.shouldRun) {
            stopOwnedSession()
            current.setMasterEnabled(false)
            return
        }

        current.setMasterEnabled(true)
        if (decision.shouldOwnSession && ownedSessionId == null) {
            ownedSessionId = current.startSession()
        }
    }

    @Synchronized
    private fun stopOwnedSession() {
        val id = ownedSessionId ?: return
        gateway?.stopSession(id)
        ownedSessionId = null
    }
}
