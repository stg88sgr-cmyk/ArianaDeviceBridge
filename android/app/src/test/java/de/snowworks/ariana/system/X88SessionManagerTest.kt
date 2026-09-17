package de.snowworks.ariana.system

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class X88SessionManagerTest {
    @Test
    fun onlyCurrentSessionCanClose() {
        val sessions = X88SessionManager()
        val id = sessions.openSession()

        assertTrue(sessions.isValid(id))
        assertFalse(sessions.closeSession("wrong"))
        assertTrue(sessions.closeSession(id))
        assertNull(sessions.currentSession())
    }
}
