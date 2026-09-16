package de.snowworks.ariana.bridge

import android.content.Context
import de.snowworks.ariana.ArianaGate
import de.snowworks.ariana.Feature
import de.snowworks.ariana.PermissionStore
import de.snowworks.ariana.presence.PresenceSignalController
import de.snowworks.ariana.session.SessionRegistry
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sanitized, read-only device capability snapshot for authenticated X-88 modules.
 *
 * Deliberately excludes payload/content data such as camera frames, microphone
 * audio, notification text, file paths, location values, Bluetooth device data,
 * pairing codes, bearer tokens, and approval grants.
 */
object ReadOnlyDeviceState {
    fun snapshot(context: Context): JSONObject {
        val app = context.applicationContext
        val gate = ArianaGate(app)
        val permissions = PermissionStore(app)

        val features = JSONArray()
        Feature.entries.forEach { feature ->
            features.put(
                JSONObject()
                    .put("id", feature.id)
                    .put("permissionGranted", permissions.isGranted(feature))
                    .put("permissionLabel", permissions.label(feature))
                    .put("sessionActive", SessionRegistry.isActive(feature)),
            )
        }

        return JSONObject()
            .put("masterEnabled", gate.isMasterEnabled)
            .put("blocked", gate.isBlocked)
            .put("presenceState", PresenceSignalController.currentState())
            .put("dialogueSessionActive", DialogueSessionStore.hasActiveSession())
            .put("dialogueProviderId", DialogueRouter.providerId())
            .put("activeFeatureCount", SessionRegistry.snapshot().size)
            .put("pendingActionApprovals", ActionApprovalStore.pendingCount())
            .put("features", features)
    }
}
