package de.snowworks.ariana.system

import org.junit.Assert.assertEquals
import org.junit.Test

class X88BackendResolverTest {
    private val resolver = X88BackendResolver()

    @Test
    fun privilegedBackendWinsWhenExplicitlyAvailable() {
        assertEquals(
            X88BackendMode.PRIVILEGED_SYSTEM,
            resolver.resolve(privilegedSystemAvailable = true, appBinderAvailable = true),
        )
    }

    @Test
    fun appBinderIsFallbackBeforeInProcess() {
        assertEquals(
            X88BackendMode.APP_BINDER,
            resolver.resolve(privilegedSystemAvailable = false, appBinderAvailable = true),
        )
        assertEquals(
            X88BackendMode.IN_PROCESS,
            resolver.resolve(privilegedSystemAvailable = false, appBinderAvailable = false),
        )
    }
}
