package de.snowworks.ariana.bridge

import android.content.Context
import android.content.Intent
import android.provider.Settings
import de.snowworks.ariana.apk.ApkInstaller
import de.snowworks.ariana.apk.ApkManager
import de.snowworks.ariana.neuro.NeuroChannel
import de.snowworks.ariana.neuro.NeuroSignal
import de.snowworks.ariana.neuro.V30NeuroRuntime
import kotlinx.coroutines.CancellationException

/**
 * Executes the small, explicit set of local device actions.
 *
 * The original synchronous API is retained for compatibility. New callers should
 * prefer executeSuspend/executeSecuredSuspend, which return the canonical
 * ArianaResult and pass through the suspend-capable X-88 result pipeline.
 */
object LocalDeviceActionExecutor {
    data class Result(
        val ok: Boolean,
        val error: String? = null,
    )

    /**
     * Trusted in-process UI path. The user confirmation grant is still mandatory;
     * only network token authentication is represented by a private local token.
     */
    fun execute(context: Context, grantId: String, action: String): Result =
        executeThroughChain(
            context = context,
            tokenAuthenticator = TokenAuthenticator { candidate -> candidate == LOCAL_UI_TOKEN },
            providedToken = LOCAL_UI_TOKEN,
            grantId = grantId,
            action = action,
            payload = emptyMap(),
        )

    /** Bridge-ready synchronous compatibility path using the actual loopback token. */
    fun executeSecured(
        context: Context,
        expectedToken: String,
        providedToken: String?,
        grantId: String,
        action: String,
        payload: Map<String, String> = emptyMap(),
    ): Result = executeThroughChain(
        context = context,
        tokenAuthenticator = ConstantTimeTokenAuthenticator(expectedToken),
        providedToken = providedToken,
        grantId = grantId,
        action = action,
        payload = payload,
    )

    /**
     * Final X-88 in-process execution path.
     * Returns ArianaResult and supports suspend dispatch, timeout and cancellation.
     */
    suspend fun executeSuspend(
        context: Context,
        grantId: String,
        action: String,
        timeoutMs: Long = ArianaResultExecutor.DEFAULT_TIMEOUT_MS,
    ): ArianaResult = executeSuspendThroughChain(
        context = context,
        tokenAuthenticator = TokenAuthenticator { candidate -> candidate == LOCAL_UI_TOKEN },
        providedToken = LOCAL_UI_TOKEN,
        grantId = grantId,
        action = action,
        payload = emptyMap(),
        timeoutMs = timeoutMs,
    )

    /** Final X-88 loopback-token path for coroutine-aware callers. */
    suspend fun executeSecuredSuspend(
        context: Context,
        expectedToken: String,
        providedToken: String?,
        grantId: String,
        action: String,
        payload: Map<String, String> = emptyMap(),
        timeoutMs: Long = ArianaResultExecutor.DEFAULT_TIMEOUT_MS,
    ): ArianaResult = executeSuspendThroughChain(
        context = context,
        tokenAuthenticator = ConstantTimeTokenAuthenticator(expectedToken),
        providedToken = providedToken,
        grantId = grantId,
        action = action,
        payload = payload,
        timeoutMs = timeoutMs,
    )

    private fun executeThroughChain(
        context: Context,
        tokenAuthenticator: TokenAuthenticator,
        providedToken: String?,
        grantId: String,
        action: String,
        payload: Map<String, String>,
    ): Result {
        val normalizedAction = action.trim().lowercase()
        if (normalizedAction !in SUPPORTED_ACTIONS) {
            return Result(false, "ACTION_NOT_IMPLEMENTED")
        }

        val app = context.applicationContext
        val chain = SecurityChain(
            tokenAuthenticator = tokenAuthenticator,
            inputValidator = ActionInputValidator(),
            policyEvaluator = AndroidPolicyEvaluator(app),
            permissionResolver = AndroidPermissionResolver(app),
            confirmationGate = LocalApprovalConfirmationGate,
            actionDispatcher = ActionDispatcher { descriptor, _ ->
                val dispatched = dispatchApproved(app, descriptor.actionId)
                DispatchResult(
                    ok = dispatched.ok,
                    code = dispatched.error,
                )
            },
        )

        return when (val decision = chain.execute(
            SecurityRequest(
                actionId = normalizedAction,
                payload = payload,
                token = providedToken,
                approvalGrantId = grantId,
            ),
        )) {
            is SecurityDecision.Dispatched -> Result(
                ok = decision.result.ok,
                error = decision.result.code,
            )

            is SecurityDecision.PendingConfirmation -> Result(
                ok = false,
                error = "APPROVAL_REQUIRED:${decision.proposalId}",
            )

            is SecurityDecision.Denied -> Result(
                ok = false,
                error = decision.code,
            )
        }
    }

    private suspend fun executeSuspendThroughChain(
        context: Context,
        tokenAuthenticator: TokenAuthenticator,
        providedToken: String?,
        grantId: String,
        action: String,
        payload: Map<String, String>,
        timeoutMs: Long,
    ): ArianaResult {
        val normalizedAction = action.trim().lowercase()
        if (normalizedAction !in SUPPORTED_ACTIONS) {
            return ArianaResult.error(
                actionId = normalizedAction.ifBlank { "invalid_action" },
                code = "ACTION_NOT_IMPLEMENTED",
                message = "Local device action is not implemented.",
            )
        }

        val app = context.applicationContext
        val chain = SuspendSecurityChain(
            tokenAuthenticator = tokenAuthenticator,
            inputValidator = ActionInputValidator(),
            policyEvaluator = AndroidPolicyEvaluator(app),
            permissionResolver = AndroidPermissionResolver(app),
            confirmationGate = LocalApprovalConfirmationGate,
            actionDispatcher = SuspendActionDispatcher { descriptor, _ ->
                val dispatched = dispatchApproved(app, descriptor.actionId)
                DispatchResult(
                    ok = dispatched.ok,
                    code = dispatched.error ?: if (dispatched.ok) "DISPATCH_OK" else "DISPATCH_FAILED",
                    message = if (dispatched.ok) "Local device action completed." else "Local device action failed.",
                )
            },
        )

        val result = X88ResultTransmuter(
            chain = chain,
            timeoutMs = timeoutMs,
        ).execute(
            SecurityRequest(
                actionId = normalizedAction,
                payload = payload,
                token = providedToken,
                approvalGrantId = grantId,
            ),
        )

        // Feed only metadata back into the V30 neuro runtime. User content,
        // payload values and result messages are intentionally not copied.
        try {
            V30NeuroRuntime.currentOrNull()?.emit(
                NeuroSignal(
                    channel = NeuroChannel.ACTION_RESULT,
                    source = "local-device-action-executor",
                    payload = mapOf(
                        "actionId" to result.actionId,
                        "ok" to result.ok.toString(),
                        "status" to result.status.name,
                        "code" to result.code,
                    ),
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            // Runtime feedback must never rewrite or mask the canonical action result.
        }

        return result
    }

    /** Called only after all SecurityChain gates have passed. */
    private fun dispatchApproved(context: Context, action: String): Result = when (action) {
        ACTION_OPEN_SETTINGS -> openSettings(context)
        ACTION_APK_INSTALL_LATEST -> installLatestTrustedApk(context)
        else -> Result(false, "ACTION_NOT_IMPLEMENTED")
    }

    private fun openSettings(context: Context): Result = runCatching {
        context.startActivity(
            Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        Result(true)
    }.getOrElse { error ->
        Result(false, error.javaClass.simpleName)
    }

    private fun installLatestTrustedApk(context: Context): Result = runCatching {
        val manager = ApkManager(context)
        val staged = manager.stageLatest() ?: return Result(false, "NO_APK_DOWNLOAD_FOUND")
        val install = ApkInstaller(context).startTrustedInstall(staged)
        if (install.ok) {
            manager.clearStaging(keepFileName = staged.name)
            Result(true)
        } else {
            Result(false, install.reason ?: "APK_INSTALL_REJECTED")
        }
    }.getOrElse { error ->
        Result(false, error.javaClass.simpleName)
    }

    const val ACTION_OPEN_SETTINGS = "open_settings"
    const val ACTION_APK_INSTALL_LATEST = "apk_install_latest"

    private const val LOCAL_UI_TOKEN = "x88-local-ui-authority"

    private val SUPPORTED_ACTIONS = setOf(
        ACTION_OPEN_SETTINGS,
        ACTION_APK_INSTALL_LATEST,
    )
}
