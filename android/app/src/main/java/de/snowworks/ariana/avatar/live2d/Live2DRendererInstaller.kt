package de.snowworks.ariana.avatar.live2d

import de.snowworks.ariana.avatar.AvatarDriver
import de.snowworks.ariana.avatar.AvatarDriverRouter

/**
 * Safe hand-off coordinator for promoting a validated Ariana Live2D model into
 * the active avatar pipeline.
 *
 * The driver factory is deliberately lazy: malformed or incomplete model
 * bundles never allocate renderer resources and can never replace the current
 * avatar output.
 */
class Live2DRendererInstaller(
    private val router: AvatarDriverRouter,
) {

    data class Result(
        val gate: Live2DInstallGate.Result,
        val installed: Boolean,
    ) {
        fun compactLabel(): String = when {
            installed -> "live2d-renderer-installed"
            !gate.ready -> gate.compactLabel()
            else -> "live2d-renderer-not-installed"
        }
    }

    fun install(
        candidate: Live2DInstallGate.Candidate,
        createDriver: () -> AvatarDriver,
    ): Result {
        val gate = Live2DInstallGate.validate(candidate)
        if (!gate.ready) {
            return Result(gate = gate, installed = false)
        }

        val driver = createDriver()
        router.install(driver)

        return Result(
            gate = gate,
            installed = router.currentDriver() === driver,
        )
    }
}
