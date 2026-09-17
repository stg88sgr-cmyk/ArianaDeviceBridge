package de.snowworks.ariana.system

import java.util.UUID

class X88SessionManager {
    private var currentSessionId: String? = null

    @Synchronized
    fun openSession(): String {
        val id = UUID.randomUUID().toString()
        currentSessionId = id
        return id
    }

    @Synchronized
    fun closeSession(sessionId: String): Boolean {
        if (currentSessionId != sessionId) return false
        currentSessionId = null
        return true
    }

    @Synchronized
    fun currentSession(): String? = currentSessionId

    @Synchronized
    fun isValid(sessionId: String?): Boolean =
        sessionId != null && sessionId == currentSessionId
}
