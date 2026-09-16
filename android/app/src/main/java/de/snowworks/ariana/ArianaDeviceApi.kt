package de.snowworks.ariana

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.provider.Settings
import de.snowworks.app.widget.ArianaWakewordService
import de.snowworks.ariana.bridge.LocalBridgeServer
import de.snowworks.ariana.session.ArianaCaptureService
import de.snowworks.ariana.session.SessionRegistry
import de.snowworks.ariana.xspace.XSpaceApprovalRequestStore
import de.snowworks.ariana.xspace.XSpaceBridgeActionRouter
import de.snowworks.ariana.xspace.XSpaceConfirmedActionExecutor
import de.snowworks.ariana.xspace.XSpaceGatewayClient
import org.json.JSONObject
import java.util.UUID

/**
 * Internal interface for permission status, start/stop, master, and errors.
 * Granting a permission does not connect any AI service.
 */
class ArianaDeviceApi(private val context: Context) {
    private val gate = ArianaGate(context)
    private val permissions = PermissionStore(context)

    fun getConnection(): ConnectionState = if (LocalBridgeServer.isRunning()) {
        ConnectionState(
            connected = true,
            label = "Lokale Bridge aktiv",
            detail = "Ariana-Bridge läuft ausschließlich auf 127.0.0.1:${LocalBridgeServer.PORT}.",
        )
    } else {
        ConnectionState.DISCONNECTED
    }

    fun isMasterEnabled(): Boolean = gate.isMasterEnabled

    fun isBlocked(): Boolean = gate.isBlocked

    fun setMasterEnabled(enabled: Boolean): ArianaResult<Unit> {
        if (!enabled) {
            stopAllSessions()
            LocalBridgeServer.stop()
        }
        gate.setMasterEnabled(enabled)
        if (enabled) LocalBridgeServer.start(context)
        return ArianaResult.Ok(Unit)
    }

    fun getStatus(feature: Feature): FeatureStatus {
        val active = SessionRegistry.isActive(feature)
        return FeatureStatus(
            feature = feature,
            permissionGranted = permissions.isGranted(feature),
            permissionLabel = permissions.label(feature),
            enabled = active,
            sessionActive = active,
            detail = if (active) "Sitzung läuft" else null,
        )
    }

    fun requiredRuntimePermissions(feature: Feature): Array<String> =
        permissions.runtimePermissions(feature)

    fun canUse(feature: Feature): ArianaResult<Unit> {
        val gated = gate.assertCanUse()
        if (gated is ArianaResult.Err) return gated
        if (feature.androidSettingsOnly && !permissions.isGranted(feature)) {
            return ArianaResult.Err(
                ArianaError(
                    ArianaError.Code.ANDROID_SETTINGS,
                    "Öffne die Systemeinstellungen und aktiviere den Benachrichtigungszugriff für Snowworks manuell.",
                ),
            )
        }
        if (feature != Feature.SCREEN &&
            feature != Feature.FILES &&
            feature != Feature.NOTIFY_READ &&
            !permissions.isGranted(feature)
        ) {
            return ArianaResult.Err(
                ArianaError(
                    ArianaError.Code.DENIED,
                    "${feature.title} ist nicht freigegeben.",
                ),
            )
        }
        return ArianaResult.Ok(Unit)
    }

    fun start(activity: Activity, feature: Feature, projectionData: Intent? = null): ArianaResult<Unit> {
        val allowed = canUse(feature)
        if (allowed is ArianaResult.Err) return allowed
        return when (feature) {
            Feature.CAMERA, Feature.MICROPHONE, Feature.SCREEN -> {
                if (feature == Feature.SCREEN && projectionData == null) {
                    return ArianaResult.Err(
                        ArianaError(
                            ArianaError.Code.ABORTED,
                            "Bildschirmübertragung braucht die Systembestätigung.",
                        ),
                    )
                }
                if (feature == Feature.SCREEN && projectionData != null) {
                    val resultCode = projectionData.getIntExtra("resultCode", Activity.RESULT_OK)
                    ArianaCaptureService.startWithProjection(activity, resultCode, projectionData)
                } else {
                    ArianaCaptureService.start(activity, feature)
                }
                ArianaResult.Ok(Unit)
            }
            Feature.NOTIFY_SEND, Feature.NOTIFY_READ, Feature.FILES, Feature.LOCATION, Feature.BLUETOOTH -> {
                SessionRegistry.markActive(feature, true)
                ArianaResult.Ok(Unit)
            }
        }
    }

    fun stop(feature: Feature): ArianaResult<Unit> {
        SessionRegistry.markActive(feature, false)
        if (feature.needsForegroundService) {
            ArianaCaptureService.stopFeature(context, feature)
        }
        return ArianaResult.Ok(Unit)
    }

    fun stopAll(): ArianaResult<Unit> {
        stopAllSessions()
        LocalBridgeServer.stop()
        gate.blockAfterStopAll()
        return ArianaResult.Ok(Unit)
    }

    /** Create a local X Space proposal. CONFIRM actions enter the visible review queue. */
    fun proposeXSpaceAction(
        action: String,
        payload: JSONObject = JSONObject(),
    ): JSONObject = XSpaceBridgeActionRouter.dispatch(
        context = context.applicationContext,
        requestId = UUID.randomUUID().toString(),
        action = action,
        payload = payload,
    )

    /** Pending X Space actions are safe, sanitized descriptions for visible UI. */
    fun pendingXSpaceApprovals(): List<XSpaceApprovalRequestStore.ReviewRequest> =
        XSpaceConfirmedActionExecutor.pending()

    /** Call only from the visible approve button for the matching proposal. */
    fun approveXSpaceAction(proposalId: String): JSONObject =
        XSpaceConfirmedActionExecutor.approveAndExecute(context, proposalId)

    /** Call only from the visible deny/cancel button. */
    fun denyXSpaceAction(proposalId: String): JSONObject =
        XSpaceConfirmedActionExecutor.deny(proposalId)

    fun createScreenCaptureIntent(): Intent {
        val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        return mgr.createScreenCaptureIntent()
    }

    fun openNotificationListenerSettings(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    fun openAppSettings(): Intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        )

    private fun stopAllSessions() {
        ArianaWakewordService.stop(context)
        ArianaCaptureService.stopAll(context)
        SessionRegistry.clear()
        XSpaceGatewayClient.disconnect()
        XSpaceConfirmedActionExecutor.revokeAll()
    }
}
