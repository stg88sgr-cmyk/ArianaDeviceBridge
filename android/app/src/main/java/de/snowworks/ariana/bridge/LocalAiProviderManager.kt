package de.snowworks.ariana.bridge

import android.content.Context
import android.net.Uri
import de.snowworks.ariana.thermal.ThermalSafetyController

/** Registers the app-private on-device model as the preferred Ariana dialogue provider. */
object LocalAiProviderManager {
    data class Status(
        val installed: Boolean,
        val enabled: Boolean,
        val active: Boolean,
        val sizeBytes: Long,
        val displayName: String?,
    )

    @Volatile private var activeProvider: LocalDialogueProvider? = null

    @Synchronized
    fun activateConfigured(context: Context): Boolean {
        if (!ThermalSafetyController.allowsLocalInference()) {
            closeRuntime()
            return false
        }

        val model = LocalModelStore(context).status()
        if (!model.enabled || model.file == null) {
            closeRuntime()
            return false
        }

        val currentId = DialogueRouter.providerId()
        if (currentId == PROVIDER_ID && activeProvider != null) return true

        closeRuntime()
        val provider = LocalDialogueProvider(context, model.file)
        val registered = DialogueRouter.register(
            PROVIDER_ID,
            DialogueRouter.LOCAL_PROVIDER_TIMEOUT_MS,
        ) { text -> provider.generate(text) }
        if (registered) {
            activeProvider = provider
            return true
        }
        provider.close()
        return false
    }

    @Synchronized
    fun importAndActivate(context: Context, uri: Uri): Status {
        closeRuntime()
        LocalModelStore(context).importModel(uri)
        activateConfigured(context)
        return status(context)
    }

    @Synchronized
    fun setEnabled(context: Context, enabled: Boolean): Status {
        LocalModelStore(context).setEnabled(enabled)
        if (enabled) {
            activateConfigured(context)
        } else {
            closeRuntime()
            if (CloudAccessGate.isEnabled()) {
                AiProviderManager.activateConfigured(context)
            } else {
                DialogueRouter.unregister()
            }
        }
        return status(context)
    }

    @Synchronized
    fun clear(context: Context): Status {
        closeRuntime()
        LocalModelStore(context).clear()
        if (CloudAccessGate.isEnabled()) {
            AiProviderManager.activateConfigured(context)
        } else {
            DialogueRouter.unregister()
        }
        return status(context)
    }

    fun status(context: Context): Status {
        val model = LocalModelStore(context).status()
        return Status(
            installed = model.installed,
            enabled = model.enabled,
            active = DialogueRouter.providerId() == PROVIDER_ID,
            sizeBytes = model.sizeBytes,
            displayName = model.displayName,
        )
    }

    @Synchronized
    fun closeRuntime() {
        activeProvider?.close()
        activeProvider = null
        if (DialogueRouter.providerId() == PROVIDER_ID) {
            DialogueRouter.unregister()
        }
    }

    const val PROVIDER_ID = "local-ai:mediapipe:gemma"
}
